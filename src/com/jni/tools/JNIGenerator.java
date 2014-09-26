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

import java.io.OutputStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.util.ArrayList;
import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.sun.tools.javah.Gen;
import com.sun.tools.javah.Mangle;
import com.sun.tools.javah.TypeSignature;
import com.sun.tools.javah.Util;


public class JNIGenerator extends Gen {
	JNIGenerator(Util util) {
        super(util);
    }

    public String getIncludes() {
        return "#include <ManagedPeer.h>";
    }

    public void write(OutputStream o, TypeElement clazz) throws Util.Exit {
        try {
            String cname = mangler.mangle(clazz.getQualifiedName(), Mangle.Type.CLASS);
            PrintWriter pw = wrapWriter(o);
            pw.println(guardBegin(cname));
            pw.println(cppGuardBegin());

            /* Write statics. */
            List<VariableElement> classfields = getAllFields(clazz);

            for (VariableElement v: classfields) {
                if (!v.getModifiers().contains(Modifier.STATIC))
                    continue;
                String s = null;
                s = defineForStatic(clazz, v);
                if (s != null) {
                    pw.println(s);
                }
            }

            /* Write methods. */
            List<ExecutableElement> classmethods = ElementFilter.methodsIn(clazz.getEnclosedElements());
            for (ExecutableElement md : classmethods) {
                if(md.getModifiers().contains(Modifier.NATIVE)){
                    TypeMirror mtr = types.erasure(md.getReturnType());
                    String sig = signature(md);
                    TypeSignature newtypesig = new TypeSignature(elems);
                    CharSequence methodName = md.getSimpleName();
                    boolean longName = false;
                    for (ExecutableElement md2: classmethods) {
                        if ((md2 != md)
                            && (methodName.equals(md2.getSimpleName()))
                            && (md2.getModifiers().contains(Modifier.NATIVE)))
                            longName = true;

                    }
                    pw.println("/*");
                    pw.println(" * Class:     " + cname);
                    pw.println(" * Method:    " +
                               mangler.mangle(methodName, Mangle.Type.FIELDSTUB));
                    pw.println(" * Signature: " + newtypesig.getTypeSignature(sig, mtr));
                    pw.println(" */");
                    pw.println("JNIEXPORT " + jniType(mtr) +
                               " JNICALL " +
                               mangler.mangleMethod(md, clazz,
                                                   (longName) ?
                                                   Mangle.Type.METHOD_JNI_LONG :
                                                   Mangle.Type.METHOD_JNI_SHORT));
                    pw.print("  (JNIEnv *, ");
                    List<? extends VariableElement> paramargs = md.getParameters();
                    List<TypeMirror> args = new ArrayList<TypeMirror>();
                    for (VariableElement p: paramargs) {
                        args.add(types.erasure(p.asType()));
                    }
                    if (md.getModifiers().contains(Modifier.STATIC))
                        pw.print("jclass");
                    else
                        pw.print("jobject");

                    for (TypeMirror arg: args) {
                        pw.print(", ");
                        pw.print(jniType(arg));
                    }
                    pw.println(");" + lineSep);
                }
            }
            pw.println(cppGuardEnd());
            pw.println(guardEnd(cname));
        } catch (TypeSignature.SignatureException e) {
            util.error("jni.sigerror", e.getMessage());
        }
    }


    protected final String jniType(TypeMirror t) throws Util.Exit {
        TypeElement throwable = elems.getTypeElement("java.lang.Throwable");
        TypeElement jClass = elems.getTypeElement("java.lang.Class");
        TypeElement jString = elems.getTypeElement("java.lang.String");
        Element tclassDoc = types.asElement(t);


        switch (t.getKind()) {
            case ARRAY: {
                TypeMirror ct = ((ArrayType) t).getComponentType();
                switch (ct.getKind()) {
                    case BOOLEAN:  return "jbooleanArray";
                    case BYTE:     return "jbyteArray";
                    case CHAR:     return "jcharArray";
                    case SHORT:    return "jshortArray";
                    case INT:      return "jintArray";
                    case LONG:     return "jlongArray";
                    case FLOAT:    return "jfloatArray";
                    case DOUBLE:   return "jdoubleArray";
                    case ARRAY:
                    case DECLARED: return "jobjectArray";
                    default: throw new Error(ct.toString());
                }
            }

            case VOID:     return "void";
            case BOOLEAN:  return "jboolean";
            case BYTE:     return "jbyte";
            case CHAR:     return "jchar";
            case SHORT:    return "jshort";
            case INT:      return "jint";
            case LONG:     return "jlong";
            case FLOAT:    return "jfloat";
            case DOUBLE:   return "jdouble";

            case DECLARED: {
                if (tclassDoc.equals(jString))
                    return "jstring";
                else if (types.isAssignable(t, throwable.asType()))
                    return "jthrowable";
                else if (types.isAssignable(t, jClass.asType()))
                    return "jclass";
                else
                    return "jobject";
            }
        }

        util.bug("jni.unknown.type");
        return null; /* dead code. */
    }
}
