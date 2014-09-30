/*
 * The MIT License (MIT)
 * 
 * Copyright (c) 2014 Jesse Benson
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
#include <precomp.h>
#include "JNIManagedPeer.h"

namespace JNI {

	static JavaVM* s_JVM = nullptr;

	void SetJVM(JavaVM* jvm)
	{
		s_JVM = jvm;
	}

	JavaVM* GetJVM()
	{
		return s_JVM;
	}

	static JNIEnv* GetEnvironment()
	{
		JNIEnv* env = nullptr;
		if (s_JVM != nullptr)
			s_JVM->AttachCurrentThread(&env, nullptr);
		return *env;
	}


	JObject::JObject(jobject object, bool releaseLocalRef)
	{
		JNIEnv* env = GetEnvironment();
		AttachObject(env, object);
		if (releaseLocalRef)
		{
			env->DeleteLocalRef(object);
		}
	}

	JObject::JObject(const JObject& object)
	{
		AttachObject(GetEnvironment(), object.m_Object);
	}
	
	JObject::JObject(JObject&& object)
		: m_Object(object.m_Object)
	{
		object.m_Object = nullptr;
	}

	JObject::~JObject()
	{
		ReleaseObject(GetEnvironment());
	}

	JObject& JObject::operator=(const JObject& object)
	{
		if (this != &object)
		{
			JNIEnv* env = GetEnvironment();
			ReleaseObject(env);
			AttachObject(env, object.m_Object);
		}
		return *this;
	}

	JObject& JObject::operator=(JObject&& object)
	{
		if (this != &object)
		{
			ReleaseObject(GetEnvironment());
			m_Object = object.m_Object;
			object.m_Object = nullptr;
		}
		return *this;
	}

	void JObject::AttachObject(JNIEnv* env, jobject object)
	{
		if (object != nullptr)
		{
			m_Object = env->NewGlobalRef(object);	
		}
	}
	
	void JObject::ReleaseObject(JNIEnv* env)
	{
		if (m_Object != nullptr)
		{
			env->DeleteGlobalRef(m_Object);
			m_Object = nullptr;
		}
	}


	JClass::JClass(const char* className)
		: JObject(GetEnvironment()->FindClass(className), /*deleteLocalRef:*/ true)
	{
	}

	JClass::~JClass()
	{
	}


	JNIManagedPeer::JNIManagedPeer(jobject object)
		: m_Object(object)
	{
	}

	JNIManagedPeer::~JNIManagedPeer()
	{
	}

	JNIEnv& JNIManagedPeer::Env()
	{
		return *GetEnvironment();
	}

} // namespace JNI
