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
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;

import com.jni.annotation.JNIClass;
import com.jni.annotation.JNIMethod;
import com.sun.tools.javah.Gen;
import com.sun.tools.javah.Mangle;
import com.sun.tools.javah.TypeSignature;
import com.sun.tools.javah.Util;

public class JNIGenerator extends Gen {
	JNIGenerator(Util util) {
		super(util);
	}

	@Override
	public String getIncludes() {
		return "#include <JNIManagedPeer.h>" + lineSeparator +
				"#include <jni.h>";
	}

	@Override
	protected String baseFileName(TypeElement clazz) {
		return super.baseFileName(clazz) + "ManagedPeer";
	}
	
	@Override
	public void writeDeclaration(OutputStream o, TypeElement clazz) throws Util.Exit {
		String cname = baseFileName(clazz);
		PrintWriter pw = wrapWriter(o);

		/* Get the desired namespace for this peer class */
		String[] namespace = getNamespace(clazz);
		pw.println(cppNamespaceBegin(namespace));
		pw.println();

		/* All ManagedPeer classes derive from the base JNI::ManagedPeer class */
		pw.println("class " + cname + " : public ::JNI::ManagedPeer");
		pw.println("{");
		pw.println("public:");
		pw.println("\t" + cname + "();");
		pw.println("\t" + "explicit " + cname + "(jobject object);");
		pw.println("\t~" + cname + "();");
		pw.println();
		pw.println("\t" + cname + "& operator=(jobject object) { ::JNI::ManagedPeer::operator=(object); return *this; }");
		pw.println();
		pw.println("\t" + "static jclass GetClass();");
		pw.println();

		/* Write declarations for methods marked with the JNIMethod annotation. */
		List<ExecutableElement> classmethods = ElementFilter.methodsIn(clazz.getEnclosedElements());
		for (ExecutableElement method : classmethods) {
			Annotation jniMethod = method.getAnnotation(JNIMethod.class);
			if (jniMethod != null) {
				String modifiers = (isStatic(method) ? "static " : "");
				String returnType = getReturnType(method);
				String methodName = getMethodName(method);
				String qualifiers = (isStatic(method) ? "" : " const");
				String argumentSignature = getArgumentsSignature(method, /*includeTypes:*/ true);
				
				pw.println("\t" + modifiers + returnType + " " + methodName + "(" + argumentSignature + ")" + qualifiers + ";");
			}
		}

		pw.println("};");
		pw.println();

		/* Close the namespace */
		pw.println(cppNamespaceEnd(namespace));
	}

	@Override
	public void writeDefinition(OutputStream o, TypeElement clazz) throws Util.Exit {
		try {
			String cname = baseFileName(clazz);
			PrintWriter pw = wrapWriter(o);
			TypeSignature typeSignature = new TypeSignature(elems);

			/* Get the desired namespace for this peer class */
			String[] namespace = getNamespace(clazz);
			pw.println(cppNamespaceBegin(namespace));
			pw.println();

			/* Default constructor */
			pw.println(cname + "::" + cname + "()");
			pw.println("{");
			pw.println("}");
			pw.println();

			/* Constructor with Java object */
			pw.println(cname + "::" + cname + "(jobject object)");
			pw.println("\t" + ": ::JNI::ManagedPeer(object)");
			pw.println("{");
			pw.println("}");
			pw.println();

			/* Destructor */
			pw.println(cname + "::~" + cname + "()");
			pw.println("{");
			pw.println("}");
			pw.println();

			/* static GetClass method - uses a static "ref counted" JClass variable to read the Java class once */
			pw.println("jclass " + cname + "::GetClass()");
			pw.println("{");
			pw.println("\t" + "static ::JNI::JClass clazz(\"" + typeSignature.getTypeSignature(clazz) + "\");");
			pw.println("\t" + "return clazz;");
			pw.println("}");
			pw.println();

			/* Write definitions for methods marked with the JNIMethod annotation. */
			List<ExecutableElement> classmethods = ElementFilter.methodsIn(clazz.getEnclosedElements());
			for (ExecutableElement method : classmethods) {
				Annotation jniMethod = method.getAnnotation(JNIMethod.class);
				if (jniMethod != null) {
					String returnType = getReturnType(method);
					String methodName = getMethodName(method);
					String qualifiers = (isStatic(method) ? "" : " const");
					String argumentSignature = getArgumentsSignature(method, /*includeTypes:*/ true);

					CharSequence methodSimpleName = method.getSimpleName();
					String methodSignature = typeSignature.getTypeSignature(signature(method), types.erasure(method.getReturnType()));

					/* Method signature */
					pw.println(returnType + " " + cname + "::" + methodName + "(" + argumentSignature + ")" + qualifiers);
					pw.println("{");

					/* Static variable to compute the jmethodID once on first use */
					pw.println("\t" + "static jmethodID methodID(Env().Get" + (isStatic(method) ? "Static" : "") + "MethodID(GetClass(), \"" + methodSimpleName + "\", \"" + methodSignature + "\"));");

					/* Generate the code to call the Java method. */
					pw.print("\t");
					pw.print(getCallSignature(method));
					pw.print("(");

					/* If the method is not static, we need a Java instance to invoke */
					if (isStatic(method))
						pw.print("GetClass(), ");
					else
						pw.print("Object(), ");
					pw.print("methodID");

					/* If the method has parameters, we need to forward the parameters */
					String arguments = getArgumentsSignature(method, /*includeTypes:*/ false);
					if (arguments != null && !arguments.isEmpty())
						pw.print(", " + arguments);
					pw.println(");");

					pw.println("}");
					pw.println();
				}
			}

			/* Close the namespace */
			pw.println(cppNamespaceEnd(namespace));
		} catch (TypeSignature.SignatureException e) {
			util.error("jni.sigerror", e.getMessage());
		}
	}

	protected final String[] getNamespace(TypeElement clazz) {
		JNIClass jniClass = clazz.getAnnotation(JNIClass.class);
		if (jniClass == null)
			util.bug("tried.to.define.non.annotated.class");
		
		String namespace = jniClass.value();
		if (namespace == null)
			util.error("JNIClass.does.not.define.namespace", clazz.getQualifiedName());
		
		return namespace.split("\\.");
	}
	
	protected final String cppNamespaceBegin(String[] namespace) {
		StringBuffer buffer = new StringBuffer();
		for (String ns : namespace) {
			buffer.append("namespace " + ns + " { ");
		}
		return buffer.toString();
	}
	
	protected final String cppNamespaceEnd(String[] namespace) {
		StringBuffer buffer = new StringBuffer();
		for (int i = 0; i < namespace.length; i++) {
			buffer.append("}");
		}
		buffer.append(" // namespace ");
		for (int i = 0; i < namespace.length; i++) {
			buffer.append(namespace[i]);
			if (i+1 < namespace.length)
				buffer.append(".");
		}
		return buffer.toString();
	}

	protected final boolean isVoid(ExecutableElement method) {
		TypeMirror returnType = types.erasure(method.getReturnType());
		return (returnType.getKind() == TypeKind.VOID);
	}
	
	protected final boolean isStatic(ExecutableElement method) {
		return method.getModifiers().contains(Modifier.STATIC);
	}
	
	protected final String getReturnType(ExecutableElement method) {
		TypeMirror returnType = types.erasure(method.getReturnType());
		return jniType(returnType);
	}
	
	protected final String getMethodName(ExecutableElement method) {
		return mangler.mangle(method.getSimpleName(), Mangle.Type.FIELDSTUB);
	}
	
	protected final String getArgumentsSignature(ExecutableElement method, boolean includeTypes) {
		StringBuffer signature = new StringBuffer();

		/* Write out the method parameters */
		List<? extends VariableElement> paramArgs = method.getParameters();
		for (int i = 0; i < paramArgs.size(); i++) {
			VariableElement param = paramArgs.get(i);
			if (includeTypes)
			{
				TypeMirror paramType = types.erasure(param.asType());
				signature.append(jniType(paramType) + " ");
			}
			signature.append(param.getSimpleName());
			if (i+1 < paramArgs.size())
				signature.append(", ");
		}

		return signature.toString();
	}
	
	private final String getCallSignature(ExecutableElement method, String baseSignature) {
		return String.format("Env().Call%s%sMethod", isStatic(method) ? "Static" : "", baseSignature);
	}
	
	protected final String getCallSignature(ExecutableElement method) {
		TypeMirror returnType = types.erasure(method.getReturnType());
		
		String baseSignature = null;
		boolean needsCast = false;
		boolean needsReturn = true;

		switch (returnType.getKind()) {
		case VOID:
			baseSignature = "Void";
			needsReturn = false;
			break;

		case ARRAY:
		case DECLARED:
			baseSignature = "Object";
			needsCast = true;
			break;

		case BOOLEAN:
			baseSignature = "Boolean";
			break;
		case BYTE:
			baseSignature = "Byte";
			break;
		case CHAR:
			baseSignature = "Char";
			break;
		case SHORT:
			baseSignature = "Short";
			break;
		case INT:
			baseSignature = "Int";
			break;
		case LONG:
			baseSignature = "Long";
			break;
		case FLOAT:
			baseSignature = "Float";
			break;
		case DOUBLE:
			baseSignature = "Double";
			break;

		default:
			util.bug("jni.unknown.type");
			return null;
		}

		StringBuffer signature = new StringBuffer();
		if (needsReturn)
			signature.append("return ");
		if (needsCast)
			signature.append("(" + getReturnType(method) + ")");
		signature.append(getCallSignature(method, baseSignature));

		return signature.toString();
	}
	
	protected final String jniType(TypeMirror type) throws Util.Exit {
		TypeElement throwable = elems.getTypeElement("java.lang.Throwable");
		TypeElement jClass = elems.getTypeElement("java.lang.Class");
		TypeElement jString = elems.getTypeElement("java.lang.String");
		Element tclassDoc = types.asElement(type);

		switch (type.getKind()) {
		case ARRAY: {
			TypeMirror ct = ((ArrayType) type).getComponentType();
			switch (ct.getKind()) {
			case BOOLEAN:
				return "jbooleanArray";
			case BYTE:
				return "jbyteArray";
			case CHAR:
				return "jcharArray";
			case SHORT:
				return "jshortArray";
			case INT:
				return "jintArray";
			case LONG:
				return "jlongArray";
			case FLOAT:
				return "jfloatArray";
			case DOUBLE:
				return "jdoubleArray";
			case ARRAY:
			case DECLARED:
				return "jobjectArray";
			default:
				throw new Error(ct.toString());
			}
		}

		case VOID:
			return "void";
		case BOOLEAN:
			return "jboolean";
		case BYTE:
			return "jbyte";
		case CHAR:
			return "jchar";
		case SHORT:
			return "jshort";
		case INT:
			return "jint";
		case LONG:
			return "jlong";
		case FLOAT:
			return "jfloat";
		case DOUBLE:
			return "jdouble";

		case DECLARED: {
			if (tclassDoc.equals(jString))
				return "jstring";
			else if (types.isAssignable(type, throwable.asType()))
				return "jthrowable";
			else if (types.isAssignable(type, jClass.asType()))
				return "jclass";
			else
				return "jobject";
		}
		}

		util.bug("jni.unknown.type");
		return null; /* dead code. */
	}
}
