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

import java.io.PrintWriter;
import java.nio.charset.Charset;
import javax.tools.DiagnosticListener;
import javax.tools.JavaFileObject;

import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.Context;

class JNIFileManager extends JavacFileManager {
    private JNIFileManager(Context context, Charset charset) {
        super(context, true, charset);
        setIgnoreSymbolFile(true);
    }

    static JNIFileManager create(final DiagnosticListener<? super JavaFileObject> dl, PrintWriter log) {
        Context javac_context = new Context();

        if (dl != null)
            javac_context.put(DiagnosticListener.class, dl);
        javac_context.put(com.sun.tools.javac.util.Log.outKey, log);

        return new JNIFileManager(javac_context, null);
    }

    void setIgnoreSymbolFile(boolean b) {
        ignoreSymbolFile = b;
    }
}
