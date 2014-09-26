/*
 * Copyright 2014 Jesse Benson
 * 
 * This code is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this work. If not, see http://www.gnu.org/licenses/.
 */
package com.jni.tools;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.annotation.Annotation;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.SimpleTypeVisitor7;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import static javax.tools.Diagnostic.Kind.*;

import com.jni.annotation.JNIClass;
import com.sun.tools.javac.code.Symbol.CompletionFailure;
import com.sun.tools.javac.main.CommandLine;
import com.sun.tools.javah.Gen;
import com.sun.tools.javah.InternalError;
import com.sun.tools.javah.NativeHeaderTool;
import com.sun.tools.javah.Util;

public class JNITask implements NativeHeaderTool.NativeHeaderTask {
	public class BadArgs extends Exception {
		private static final long serialVersionUID = 1479361270874789045L;

		BadArgs(String key, Object... args) {
			super(JNITask.this.getMessage(key, args));
			this.key = key;
			this.args = args;
		}

		BadArgs showUsage(boolean b) {
			showUsage = b;
			return this;
		}

		final String key;
		final Object[] args;
		boolean showUsage;
	}

	static abstract class Option {
		Option(boolean hasArg, String... aliases) {
			this.hasArg = hasArg;
			this.aliases = aliases;
		}

		boolean isHidden() {
			return false;
		}

		boolean matches(String opt) {
			for (String alias : aliases) {
				if (alias.equals(opt))
					return true;
			}
			return false;
		}

		boolean ignoreRest() {
			return false;
		}

		abstract void process(JNITask task, String opt, String arg)
				throws BadArgs;

		final boolean hasArg;
		final String[] aliases;
	}

	static abstract class HiddenOption extends Option {
		HiddenOption(boolean hasArg, String... aliases) {
			super(hasArg, aliases);
		}

		@Override
		boolean isHidden() {
			return true;
		}
	}

	static Option[] recognizedOptions = {

		new Option(true, "-d") {
			void process(JNITask task, String opt, String arg) {
				task.odir = new File(arg);
			}
		},
	
		new Option(false, "-v", "-verbose") {
			void process(JNITask task, String opt, String arg) {
				task.verbose = true;
			}
		},
	
		new Option(false, "-h", "-help", "--help", "-?") {
			void process(JNITask task, String opt, String arg) {
				task.help = true;
			}
		},

		new Option(false, "-version") {
			void process(JNITask task, String opt, String arg) {
				task.version = true;
			}
		},
	
		new HiddenOption(false, "-fullversion") {
			void process(JNITask task, String opt, String arg) {
				task.fullVersion = true;
			}
		},
	
		new Option(false, "-force") {
			void process(JNITask task, String opt, String arg) {
				task.force = true;
			}
		},

		new HiddenOption(false) {
			boolean matches(String opt) {
				return opt.startsWith("-XD");
			}
	
			void process(JNITask task, String opt, String arg) {
				task.javac_extras.add(opt);
			}
		},
	};

	JNITask() {
	}

	JNITask(Writer out, JavaFileManager fileManager,
			DiagnosticListener<? super JavaFileObject> diagnosticListener,
			Iterable<String> options, Iterable<String> classes) {
		this();
		this.log = getPrintWriterForWriter(out);
		this.fileManager = fileManager;
		this.diagnosticListener = diagnosticListener;

		try {
			handleOptions(options, false);
		} catch (BadArgs e) {
			throw new IllegalArgumentException(e.getMessage());
		}

		this.classes = new ArrayList<String>();
		if (classes != null) {
			for (String classname : classes) {
				classname.getClass(); // null-check
				this.classes.add(classname);
			}
		}
	}

	public void setLocale(Locale locale) {
		if (locale == null)
			locale = Locale.getDefault();
		task_locale = locale;
	}

	public void setLog(PrintWriter log) {
		this.log = log;
	}

	public void setLog(OutputStream s) {
		setLog(getPrintWriterForStream(s));
	}

	static PrintWriter getPrintWriterForStream(OutputStream s) {
		return new PrintWriter(s, true);
	}

	static PrintWriter getPrintWriterForWriter(Writer w) {
		if (w == null)
			return getPrintWriterForStream(null);
		else if (w instanceof PrintWriter)
			return (PrintWriter) w;
		else
			return new PrintWriter(w, true);
	}

	public void setDiagnosticListener(DiagnosticListener<? super JavaFileObject> dl) {
		diagnosticListener = dl;
	}

	public void setDiagnosticListener(OutputStream s) {
		setDiagnosticListener(getDiagnosticListenerForStream(s));
	}

	private DiagnosticListener<JavaFileObject> getDiagnosticListenerForStream(OutputStream s) {
		return getDiagnosticListenerForWriter(getPrintWriterForStream(s));
	}

	private DiagnosticListener<JavaFileObject> getDiagnosticListenerForWriter(Writer writer) {
		final PrintWriter pw = getPrintWriterForWriter(writer);
		return new DiagnosticListener<JavaFileObject>() {
			public void report(Diagnostic<? extends JavaFileObject> diagnostic) {
				if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
					pw.print(getMessage("err.prefix"));
					pw.print(" ");
				}
				pw.println(diagnostic.getMessage(null));
			}
		};
	}

	int run(String[] args) {
		try {
			handleOptions(args);
			boolean ok = run();
			return ok ? 0 : 1;
		} catch (BadArgs e) {
			diagnosticListener.report(createDiagnostic(e.key, e.args));
			return 1;
		} catch (InternalError e) {
			diagnosticListener.report(createDiagnostic("err.internal.error", e.getMessage()));
			return 1;
		} catch (Util.Exit e) {
			return e.exitValue;
		} finally {
			log.flush();
		}
	}

	public void handleOptions(String[] args) throws BadArgs {
		handleOptions(Arrays.asList(args), true);
	}

	private void handleOptions(Iterable<String> args, boolean allowClasses) throws BadArgs {
		if (log == null) {
			log = getPrintWriterForStream(System.out);
			if (diagnosticListener == null)
				diagnosticListener = getDiagnosticListenerForStream(System.err);
		} else {
			if (diagnosticListener == null)
				diagnosticListener = getDiagnosticListenerForWriter(log);
		}

		if (fileManager == null)
			fileManager = getDefaultFileManager(diagnosticListener, log);

		Iterator<String> iter = expandAtArgs(args).iterator();
		noArgs = !iter.hasNext();

		while (iter.hasNext()) {
			String arg = iter.next();
			if (arg.startsWith("-")) {
				handleOption(arg, iter);
			} else if (allowClasses) {
				if (classes == null)
					classes = new ArrayList<String>();
				classes.add(arg);
				while (iter.hasNext())
					classes.add(iter.next());
			} else {
				throw new BadArgs("err.unknown.option", arg).showUsage(true);
			}
		}

		if ((classes == null || classes.size() == 0) && !(noArgs || help || version || fullVersion)) {
			throw new BadArgs("err.no.classes.specified");
		}

		if (odir == null)
			throw new BadArgs("err.no.dir.specified");
	}

	private void handleOption(String name, Iterator<String> rest) throws BadArgs {
		for (Option o : recognizedOptions) {
			if (o.matches(name)) {
				if (o.hasArg) {
					if (rest.hasNext())
						o.process(this, name, rest.next());
					else
						throw new BadArgs("err.missing.arg", name).showUsage(true);
				} else {
					o.process(this, name, null);
				}

				if (o.ignoreRest()) {
					while (rest.hasNext())
						rest.next();
				}
				return;
			}
		}

		if (!fileManager.handleOption(name, rest))
			throw new BadArgs("err.unknown.option", name).showUsage(true);
	}

	private Iterable<String> expandAtArgs(Iterable<String> args) throws BadArgs {
		try {
			List<String> list = new ArrayList<String>();
			for (String arg : args)
				list.add(arg);
			return Arrays.asList(CommandLine.parse(list.toArray(new String[list.size()])));
		} catch (FileNotFoundException e) {
			throw new BadArgs("at.args.file.not.found", e.getLocalizedMessage());
		} catch (IOException e) {
			throw new BadArgs("at.args.io.exception", e.getLocalizedMessage());
		}
	}

	public Boolean call() {
		return run();
	}

	public boolean run() throws Util.Exit {
		Util util = new Util(log, diagnosticListener);

		if (noArgs || help) {
			showHelp();
			return help; // treat noArgs as an error for purposes of exit code
		}

		if (version || fullVersion) {
			showVersion(fullVersion);
			return true;
		}

		util.verbose = verbose;

		Gen generator = new JNIGenerator(util);

		if (odir != null) {
			if (!(fileManager instanceof StandardJavaFileManager)) {
				diagnosticListener.report(createDiagnostic("err.cant.use.option.for.fm", "-d"));
				return false;
			}

			if (!odir.exists()) {
				if (!odir.mkdirs())
					util.error("cant.create.dir", odir.toString());
			}

			try {
				((StandardJavaFileManager) fileManager).setLocation(StandardLocation.CLASS_OUTPUT, Collections.singleton(odir));
			} catch (IOException e) {
				Object msg = e.getLocalizedMessage();
				if (msg == null) {
					msg = e;
				}

				diagnosticListener.report(createDiagnostic("err.ioerror", odir, msg));
				return false;
			}
		}
		generator.setFileManager(fileManager);

		/*
		 * Force set to false will turn off smarts about checking file content
		 * before writing.
		 */
		generator.setForce(force);

		if (fileManager instanceof JNIFileManager)
			((JNIFileManager) fileManager).setIgnoreSymbolFile(true);

		JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
		List<String> opts = new ArrayList<String>();
		opts.add("-proc:only");
		opts.addAll(javac_extras);
		CompilationTask task = compiler.getTask(log, fileManager, diagnosticListener, opts, internalize(classes), null);
		JNIProcessor processor = new JNIProcessor(generator);
		task.setProcessors(Collections.singleton(processor));

		boolean ok = task.call();
		if (processor.exit != null)
			throw new Util.Exit(processor.exit);
		return ok;
	}

	private List<String> internalize(List<String> classes) {
		List<String> list = new ArrayList<String>();
		for (String clazz : classes) {
			list.add(clazz.replace('$', '.'));
		}
		return list;
	}

	private List<File> pathToFiles(String path) {
		List<File> files = new ArrayList<File>();
		for (String file : path.split(File.pathSeparator)) {
			if (file.length() > 0)
				files.add(new File(file));
		}
		return files;
	}

	static StandardJavaFileManager getDefaultFileManager(final DiagnosticListener<? super JavaFileObject> dl, PrintWriter log) {
		return JNIFileManager.create(dl, log);
	}

	private void showHelp() {
		log.println(getMessage("main.usage", progname));
		for (Option option : recognizedOptions) {
			if (option.isHidden())
				continue;
			String name = option.aliases[0].substring(1); // there must always be at least one name
			log.println(getMessage("main.opt." + name));
		}

		String[] fmOptions = { "-classpath", "-bootclasspath" };
		for (String option : fmOptions) {
			if (fileManager.isSupportedOption(option) == -1)
				continue;
			String name = option.substring(1);
			log.println(getMessage("main.opt." + name));
		}

		log.println(getMessage("main.usage.foot"));
	}

	private void showVersion(boolean full) {
		log.println(version(full));
	}

	private static final String versionRBName = "com.sun.tools.javah.resources.version";
	private static ResourceBundle versionRB;

	private String version(boolean full) {
		String msgKey = (full ? "javah.fullVersion" : "javah.version");
		String versionKey = (full ? "full" : "release");
		// versionKey=product: mm.nn.oo[-milestone]
		// versionKey=full: mm.mm.oo[-milestone]-build
		if (versionRB == null) {
			try {
				versionRB = ResourceBundle.getBundle(versionRBName);
			} catch (MissingResourceException e) {
				return getMessage("version.resource.missing", System.getProperty("java.version"));
			}
		}
		try {
			return getMessage(msgKey, "javah", versionRB.getString(versionKey));
		} catch (MissingResourceException e) {
			return getMessage("version.unknown", System.getProperty("java.version"));
		}
	}

	private Diagnostic<JavaFileObject> createDiagnostic(final String key, final Object... args) {
		return new Diagnostic<JavaFileObject>() {
			public Kind getKind() {
				return Diagnostic.Kind.ERROR;
			}

			public JavaFileObject getSource() {
				return null;
			}

			public long getPosition() {
				return Diagnostic.NOPOS;
			}

			public long getStartPosition() {
				return Diagnostic.NOPOS;
			}

			public long getEndPosition() {
				return Diagnostic.NOPOS;
			}

			public long getLineNumber() {
				return Diagnostic.NOPOS;
			}

			public long getColumnNumber() {
				return Diagnostic.NOPOS;
			}

			public String getCode() {
				return key;
			}

			public String getMessage(Locale locale) {
				return JNITask.this.getMessage(locale, key, args);
			}

		};
	}

	private String getMessage(String key, Object... args) {
		return getMessage(task_locale, key, args);
	}

	private String getMessage(Locale locale, String key, Object... args) {
		if (bundles == null) {
			// could make this a HashMap<Locale,SoftReference<ResourceBundle>>
			// and for efficiency, keep a hard reference to the bundle for the
			// task
			// locale
			bundles = new HashMap<Locale, ResourceBundle>();
		}

		if (locale == null)
			locale = Locale.getDefault();

		ResourceBundle b = bundles.get(locale);
		if (b == null) {
			try {
				b = ResourceBundle.getBundle("com.sun.tools.javah.resources.l10n", locale);
				bundles.put(locale, b);
			} catch (MissingResourceException e) {
				throw new InternalError("Cannot find javah resource bundle for locale " + locale, e);
			}
		}

		try {
			return MessageFormat.format(b.getString(key), args);
		} catch (MissingResourceException e) {
			return key;
		}
	}

	File odir;
	List<String> classes;
	boolean verbose;
	boolean noArgs;
	boolean help;
	boolean version;
	boolean fullVersion;
	boolean force;
	Set<String> javac_extras = new LinkedHashSet<String>();

	PrintWriter log;
	JavaFileManager fileManager;
	DiagnosticListener<? super JavaFileObject> diagnosticListener;
	Locale task_locale;
	Map<Locale, ResourceBundle> bundles;

	private static final String progname = "javah";

	@SupportedAnnotationTypes("*")
	class JNIProcessor extends AbstractProcessor {

		JNIProcessor(Gen generator) {
			this.mGenerator = generator;
		}

		@Override
		public SourceVersion getSupportedSourceVersion() {
			// since this is co-bundled with javac, we can assume it supports
			// the latest source version
			return SourceVersion.latest();
		}

		@Override
		public void init(ProcessingEnvironment pEnv) {
			super.init(pEnv);
			mMessager = processingEnv.getMessager();
		}

		public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
			try {
				Set<TypeElement> classes = getAllJNIClasses(ElementFilter.typesIn(roundEnv.getRootElements()));
				if (classes.size() > 0) {
					checkMethodParameters(classes);
					mGenerator.setProcessingEnvironment(processingEnv);
					mGenerator.setClasses(classes);
					mGenerator.run();
				}
			} catch (CompletionFailure cf) {
				mMessager.printMessage(ERROR, getMessage("class.not.found", cf.sym.getQualifiedName().toString()));
			} catch (ClassNotFoundException cnfe) {
				mMessager.printMessage(ERROR, getMessage("class.not.found", cnfe.getMessage()));
			} catch (IOException ioe) {
				mMessager.printMessage(ERROR, getMessage("io.exception", ioe.getMessage()));
			} catch (Util.Exit e) {
				exit = e;
			}

			return true;
		}

		private Set<TypeElement> getAllJNIClasses(Set<? extends TypeElement> classes) {
			Set<TypeElement> allClasses = new LinkedHashSet<TypeElement>();
			getAllJNIClasses(classes, allClasses);
			return allClasses;
		}

		private void getAllJNIClasses(Iterable<? extends TypeElement> classes, Set<TypeElement> allClasses) {
			for (TypeElement clazz : classes) {
				Annotation annotation = clazz.getAnnotation(JNIClass.class);
				if (annotation != null)
				{
					JNIClass jniclass = (JNIClass) annotation;
					String namespace = jniclass.value();

					allClasses.add(clazz);
					getAllJNIClasses(ElementFilter.typesIn(clazz.getEnclosedElements()), allClasses);
				}
			}
		}

		// 4942232:
		// check that classes exist for all the parameters of native methods
		private void checkMethodParameters(Set<TypeElement> classes) {
			Types types = processingEnv.getTypeUtils();
			for (TypeElement te : classes) {
				for (ExecutableElement ee : ElementFilter.methodsIn(te.getEnclosedElements())) {
					for (VariableElement ve : ee.getParameters()) {
						TypeMirror tm = ve.asType();
						checkMethodParametersVisitor.visit(tm, types);
					}
				}
			}
		}

		private TypeVisitor<Void, Types> checkMethodParametersVisitor = new SimpleTypeVisitor7<Void, Types>() {
			@Override
			public Void visitArray(ArrayType type, Types types) {
				visit(type.getComponentType(), types);
				return null;
			}

			@Override
			public Void visitDeclared(DeclaredType type, Types types) {
				type.asElement().getKind(); // ensure class exists
				for (TypeMirror st : types.directSupertypes(type))
					visit(st, types);
				return null;
			}
		};

		private Messager mMessager;
		private Gen mGenerator;
		private Util.Exit exit;
	}
}
