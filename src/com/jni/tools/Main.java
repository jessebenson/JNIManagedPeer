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

public class Main {
 
	static {
		// Set the JDK home to run this - required to get the Java compiler at runtime
        System.setProperty("java.home", "C:\\Program Files\\Java\\jdk1.7.0_67");
	}
	
    public static void main(String[] args) {
        JNITask t = new JNITask();
        int rc = t.run(args);
        System.exit(rc);
    }

    public static int run(String[] args, PrintWriter out) {
    	JNITask t = new JNITask();
        t.setLog(out);
        return t.run(args);
    }
}
