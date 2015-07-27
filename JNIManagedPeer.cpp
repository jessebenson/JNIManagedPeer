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
#include "stdafx.h"
#include "JNIManagedPeer.h"

namespace JNI {

    static JavaVM* s_JVM = nullptr;

    void STDMETHODCALLTYPE SetJVM(JavaVM* jvm)
    {
        s_JVM = jvm;
    }

    JavaVM* STDMETHODCALLTYPE GetJVM()
    {
        return s_JVM;
    }

    static JNIEnv* GetEnvironment()
    {
        JNIEnv* env = nullptr;
        if (s_JVM != nullptr)
            s_JVM->AttachCurrentThread(reinterpret_cast<void**>(&env), nullptr);
        return env;
    }


    JObject::JObject()
    {
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

    JObject& JObject::operator=(jobject object)
    {
        JNIEnv* env = GetEnvironment();
        ReleaseObject(env);
        AttachObject(env, object);
        return *this;
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

    void JObject::AttachLocalObject(JNIEnv* env, jobject object)
    {
        if (object != nullptr)
        {
            m_Object = env->NewGlobalRef(object);
            env->DeleteLocalRef(object);
        }
    }


    JClass::JClass(const char* className)
        : JObject(GetEnvironment()->FindClass(className), /*deleteLocalRef:*/ true)
    {
    }

    JClass::~JClass()
    {
    }


    JString::JString(jstring string, bool removeLocalRef)
        : JObject(string, removeLocalRef)
    {
    }

    JString::JString(const char* content)
    {
        JNIEnv* env = GetEnvironment();
        AttachLocalObject(env, env->NewStringUTF(content));
    }

    JString::JString(const wchar_t* content)
    {
        JNIEnv* env = GetEnvironment();
        AttachLocalObject(env, env->NewString((const jchar *)content, wcslen(content)));
    }

    JString::~JString()
    {
        Clear();
    }

    const char* JString::GetUTFString() const
    {
        if (m_pString == nullptr)
        {
            // Logically, this method doesn't change the JString
            const_cast<JString*>(this)->m_pString = GetEnvironment()->GetStringUTFChars(String(), nullptr);
        }

        return m_pString;
    }

    int JString::GetUTFLength() const
    {
        return (int)GetEnvironment()->GetStringUTFLength(String());
    }

    const wchar_t* JString::GetStringChars() const
    {
        if (m_pWString == nullptr)
        {
            // Logically, this method doesn't change the JString
            const_cast<JString*>(this)->m_pWString = (const wchar_t*)GetEnvironment()->GetStringChars(String(), nullptr);
        }

        return m_pWString;
    }

    int JString::GetLength() const
    {
        return (int)GetEnvironment()->GetStringLength(String());
    }

    void JString::Clear()
    {
        if (m_pString != nullptr && String() != nullptr)
        {
            GetEnvironment()->ReleaseStringUTFChars(String(), m_pString);
            m_pString = nullptr;
        }

        if (m_pWString != nullptr && String() != nullptr)
        {
            GetEnvironment()->ReleaseStringChars(String(), (const jchar*)m_pWString);
            m_pWString = nullptr;
        }
    }


    ManagedPeer::ManagedPeer()
        : m_Object(nullptr)
    {
    }

    ManagedPeer::ManagedPeer(jobject object)
        : m_Object(object)
    {
    }

    ManagedPeer::~ManagedPeer()
    {
    }

    ManagedPeer& ManagedPeer::operator=(jobject obj)
    {
        m_Object = obj;
        return *this;
    }

    JNIEnv& ManagedPeer::Env()
    {
        return *GetEnvironment();
    }

} // namespace JNI
