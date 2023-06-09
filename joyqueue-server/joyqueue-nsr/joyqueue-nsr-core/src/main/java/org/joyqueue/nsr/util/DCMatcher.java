/**
 * Copyright 2019 The JoyQueue Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.joyqueue.nsr.util;


import com.jd.laf.extension.Type;
import org.joyqueue.toolkit.URL;

/**
 * 数据中心匹配器
 */
public interface DCMatcher extends Type<String> {
    /**
     * 根据IP进行匹配
     *
     * @param ip
     * @return
     */
    boolean match(String ip);
    /**
     * 设置匹配规则
     *
     * @param url
     * @return
     */
    void setUrl(URL url);
}