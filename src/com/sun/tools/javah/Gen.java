/*
 * Copyright (c) 2002, 2009, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.javah;

import java.io.UnsupportedEncodingException;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import javax.annotation.processing.ProcessingEnvironment;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.StandardLocation;

/**
 * An abstraction for generating support files required by native methods.
 * Subclasses are for specific native interfaces. At the time of its
 * original writing, this interface is rich enough to support JNI and the
 * old 1.0-style native method interface.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own
 * risk.  This code and its internal interfaces are subject to change
 * or deletion without notice.</b></p>
 *
 * @author  Sucheta Dambalkar(Revised)
 */
public abstract class Gen {
	protected String lineSeparator = System.getProperty("line.separator");

	protected ProcessingEnvironment processingEnvironment;
	protected Types types;
	protected Elements elems;
	protected Mangle mangler;
	protected Util util;

	protected Gen(Util util) {
		this.util = util;
	}

	/*
	 * List of classes for which we must generate output.
	 */
	protected Set<TypeElement> classes;
	protected String pch;
	static private final boolean isWindows = System.getProperty("os.name").startsWith("Windows");


	/**
	 * Override this abstract method, generating content for the class declaration (i.e. header)
	 * of the named class into the outputstream.
	 */
	protected abstract void writeDeclaration(OutputStream o, TypeElement clazz) throws Util.Exit;

	/**
	 * Override this abstract method, generating content for the class definition (i.e. cpp)
	 * of the named class into the outputstream.
	 */
	protected abstract void writeDefinition(OutputStream o, TypeElement clazz) throws Util.Exit;

	/**
	 * Override this method to provide a list of #include statements
	 * required by the native interface.
	 */
	protected abstract String getIncludes();

	/*
	 * Output location.
	 */
	protected JavaFileManager fileManager;

	public void setFileManager(JavaFileManager fm) {
		fileManager = fm;
	}

	public void setClasses(Set<TypeElement> classes) {
		this.classes = classes;
	}
	
	public void setPrecompiledHeader(String pch) {
		this.pch = pch;
	}

	public void setProcessingEnvironment(ProcessingEnvironment pEnv) {
		processingEnvironment = pEnv;
		elems = pEnv.getElementUtils();
		types = pEnv.getTypeUtils();
		mangler = new Mangle(elems, types);
	}

	/*
	 * Smartness with generated files.
	 */
	protected boolean force = false;

	public void setForce(boolean state) {
		force = state;
	}

	/**
	 * We explicitly need to write ASCII files because that is what C
	 * compilers understand.
	 */
	protected PrintWriter wrapWriter(OutputStream o) throws Util.Exit {
		try {
			return new PrintWriter(new OutputStreamWriter(o, "ISO8859_1"), true);
		} catch (UnsupportedEncodingException use) {
			util.bug("encoding.iso8859_1.not.found");
			return null; /* dead code */
		}
	}

	/**
	 * After initializing state of an instance, use this method to start
	 * processing.
	 *
	 * Buffer size chosen as an approximation from a single sampling of:
	 *         expr `du -sk` / `ls *.h | wc -l`
	 */
	public void run() throws IOException, ClassNotFoundException, Util.Exit {
		/* Each class goes to its own files... */
		for (TypeElement type : classes) {
			/* Write the header file and declaration */
			writeHeader(type);
			/* Write the cpp file and definition */
			writeCpp(type);
		}
	}

	/*
	 * Generate the declaration for the given type and write it to a C++ header file.
	 */
	private void writeHeader(TypeElement type) throws IOException, ClassNotFoundException, Util.Exit {
		String filename = baseFileName(type.getSimpleName()) + ".h";
		ByteArrayOutputStream bout = new ByteArrayOutputStream(8192);
		writeHeaderBegin(bout);
		writeDeclaration(bout, type);
		writeIfChanged(bout.toByteArray(), getFileObject(filename));
	}

	/*
	 * Generate the definition for the given type and write it to a C++ code file.
	 */
	private void writeCpp(TypeElement type) throws IOException, ClassNotFoundException, Util.Exit {
		String filename = baseFileName(type.getSimpleName()) + ".cpp";
		ByteArrayOutputStream bout = new ByteArrayOutputStream(8192);
		writeCppBegin(bout);
		writeDefinition(bout, type);
		writeIfChanged(bout.toByteArray(), getFileObject(filename));
	}
	
	/*
	 * Write the contents of byte[] b to a file named file.  Writing
	 * is done if either the file doesn't exist or if the contents are
	 * different.
	 */
	private void writeIfChanged(byte[] b, FileObject file) throws IOException {
		boolean mustWrite = false;
		String event = "[No need to update file ";

		if (force) {
			mustWrite = true;
			event = "[Forcefully writing file ";
		} else {
			InputStream in;
			byte[] a;
			try {
				// regrettably, there's no API to get the length in bytes
				// for a FileObject, so we can't short-circuit reading the
				// file here
				in = file.openInputStream();
				a = readBytes(in);
				if (!Arrays.equals(a, b)) {
					mustWrite = true;
					event = "[Overwriting file ";
				}
			} catch (FileNotFoundException e) {
				mustWrite = true;
				event = "[Creating file ";
			}
		}

		if (util.verbose)
			util.log(event + file + "]");

		if (mustWrite) {
			OutputStream out = file.openOutputStream();
			out.write(b); /* No buffering, just one big write! */
			out.close();
		}
	}

	private byte[] readBytes(InputStream in) throws IOException {
		try {
			byte[] array = new byte[in.available() + 1];
			int offset = 0;
			int n;
			while ((n = in.read(array, offset, array.length - offset)) != -1) {
				offset += n;
				if (offset == array.length)
					array = Arrays.copyOf(array, array.length * 2);
			}

			return Arrays.copyOf(array, offset);
		} finally {
			in.close();
		}
	}

	protected String defineForStatic(TypeElement clazz, VariableElement field) throws Util.Exit {
		CharSequence cnamedoc = clazz.getQualifiedName();
		CharSequence fnamedoc = field.getSimpleName();

		String cname = mangler.mangle(cnamedoc, Mangle.Type.CLASS);
		String fname = mangler.mangle(fnamedoc, Mangle.Type.FIELDSTUB);

		if (!field.getModifiers().contains(Modifier.STATIC))
			util.bug("tried.to.define.non.static");

		if (field.getModifiers().contains(Modifier.FINAL)) {
			Object value = null;

			value = field.getConstantValue();

			if (value != null) { /* so it is a ConstantExpression */
				String constString = null;
				if ((value instanceof Integer) || (value instanceof Byte) || (value instanceof Short)) {
					/* covers byte, short, int */
					constString = value.toString() + "L";
				} else if (value instanceof Boolean) {
					constString = ((Boolean) value) ? "1L" : "0L";
				} else if (value instanceof Character) {
					Character ch = (Character) value;
					constString = String.valueOf(((int) ch) & 0xffff) + "L";
				} else if (value instanceof Long) {
					// Visual C++ supports the i64 suffix, not LL.
					if (isWindows)
						constString = value.toString() + "i64";
					else
						constString = value.toString() + "LL";
				} else if (value instanceof Float) {
					/* bug for bug */
					float fv = ((Float)value).floatValue();
					if (Float.isInfinite(fv))
						constString = ((fv < 0) ? "-" : "") + "Inff";
					else
						constString = value.toString() + "f";
				} else if (value instanceof Double) {
					/* bug for bug */
					double d = ((Double)value).doubleValue();
					if (Double.isInfinite(d))
						constString = ((d < 0) ? "-" : "") + "InfD";
					else
						constString = value.toString();
				}
				if (constString != null) {
					StringBuffer s = new StringBuffer("#undef ");
					s.append(cname); s.append("_"); s.append(fname); s.append(lineSeparator);
					s.append("#define "); s.append(cname); s.append("_");
					s.append(fname); s.append(" "); s.append(constString);
					return s.toString();
				}

			}
		}
		return null;
	}

	/*
	 * File name and file preamble related operations.
	 */
	private String getFileTop() {
		return "/* DO NOT EDIT THIS FILE - it is machine generated */";
	}
	
	private void writeHeaderBegin(OutputStream o) {
		PrintWriter pw = wrapWriter(o);
		pw.println(getFileTop());
		pw.println("#pragma once");
		pw.println();
		pw.println(getIncludes());
		pw.println();
	}

	private void writeCppBegin(OutputStream o) {
		PrintWriter pw = wrapWriter(o);
		pw.println(getFileTop());
		if (pch != null)
			pw.println("#include <" + pch + ">");
		pw.println();
	}

	protected String baseFileName(CharSequence className) {
		return mangler.mangle(className, Mangle.Type.CLASS);
	}

	private FileObject getFileObject(String filename) throws IOException {
		return fileManager.getFileForOutput(StandardLocation.SOURCE_OUTPUT, "", filename, null);
	}

	/**
	 * Including super classes' fields.
	 */
	public List<VariableElement> getAllFields(TypeElement subclazz) {
		List<VariableElement> fields = new ArrayList<VariableElement>();
		TypeElement cd = null;
		Stack<TypeElement> s = new Stack<TypeElement>();

		cd = subclazz;
		while (true) {
			s.push(cd);
			TypeElement c = (TypeElement) (types.asElement(cd.getSuperclass()));
			if (c == null)
				break;
			cd = c;
		}

		while (!s.empty()) {
			cd = s.pop();
			fields.addAll(ElementFilter.fieldsIn(cd.getEnclosedElements()));
		}

		return fields;
	}

	// c.f. MethodDoc.signature
	public String signature(ExecutableElement e) {
		StringBuffer sb = new StringBuffer("(");
		String sep = "";
		for (VariableElement p : e.getParameters()) {
			sb.append(sep);
			sb.append(types.erasure(p.asType()).toString());
			sep = ",";
		}
		sb.append(")");
		return sb.toString();
	}
}

