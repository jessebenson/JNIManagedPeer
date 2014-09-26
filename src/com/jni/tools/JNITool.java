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

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import javax.lang.model.SourceVersion;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;

import com.sun.tools.javah.NativeHeaderTool;
import com.sun.tools.javah.NativeHeaderTool.NativeHeaderTask;

public class JNITool implements NativeHeaderTool {

	public NativeHeaderTask getTask(Writer out,
			JavaFileManager fileManager,
			DiagnosticListener<? super JavaFileObject> diagnosticListener,
			Iterable<String> options,
			Iterable<String> classes) {
		return new JNITask(out, fileManager, diagnosticListener, options, classes);
	}

	public StandardJavaFileManager getStandardFileManager(DiagnosticListener<? super JavaFileObject> diagnosticListener, Locale locale, Charset charset) {
		return JNITask.getDefaultFileManager(diagnosticListener, null);
	}

	public int run(InputStream in, OutputStream out, OutputStream err, String... arguments) {
		JNITask task = new JNITask(
				JNITask.getPrintWriterForStream(out),
				null,
				null,
				Arrays.asList(arguments),
				null);
		return (task.run() ? 0 : 1);
	}

	public Set<SourceVersion> getSourceVersions() {
		return EnumSet.allOf(SourceVersion.class);
	}

	public int isSupportedOption(String option) {
		JNITask.Option[] options = JNITask.recognizedOptions;
		for (int i = 0; i < options.length; i++) {
			if (options[i].matches(option))
				return (options[i].hasArg ? 1 : 0);
		}
		return -1;
	}
}
