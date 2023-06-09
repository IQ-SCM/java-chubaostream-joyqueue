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
package org.joyqueue.broker.config;

import org.joyqueue.toolkit.config.Property;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Equal split property
 *
 **/
public class EqualSplitPropertyParser implements PropertyParser {
    protected static final Logger logger = LoggerFactory.getLogger(EqualSplitPropertyParser.class);

    private static final String SPLIT="=";
    private static  final int LENGTH= 2;

    @Override
    public Property parse(String property) throws Exception{
        if(property==null){
            return null;
        }
        String[] kv=property.split(SPLIT);
        if(kv.length == LENGTH){
            return  new Property(null,kv[0],kv[1],0,0);
        }else{
            //throw new IllegalArgumentException(String.format("override properties with key%value ", SPLIT));
            logger.info("ignore property {}",property);
            return null;
        }
    }
}
