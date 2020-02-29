/*
 * Copyright 2015 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package example.repackagelib;

import jsinterop.annotations.JsType;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Calls a class that is unsupported JDK (java.io.File) that is replaced by a example.java.io.File at
 * transpile time.
 */
@JsType
public class HelloWorld {

    public static String getHelloWorld() {
        return "Should say java.io.File path=hello.txt -> " + new File("hello.txt").toString();
    }
}