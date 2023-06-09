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
package org.joyqueue.handler.routing.validate;

import com.jd.laf.web.vertx.RoutingHandler;
import io.vertx.ext.web.RoutingContext;
import org.joyqueue.handler.Constants;
import org.joyqueue.handler.error.ConfigException;
import org.joyqueue.handler.error.ErrorCode;
import org.joyqueue.model.domain.User;
import org.joyqueue.model.domain.User.UserRole;

/**
 * 验证系统管理所需要的管理员角色
 * Created by yangyang115 on 18-8-3.
 */
public class ValidateAdminHandler implements RoutingHandler {

    @Override
    public String type() {
        return "validateAdmin";
    }

    @Override
    public void handle(final RoutingContext context) {
        User user = context.get(Constants.USER_KEY);
        if (user == null || user.getRole() != UserRole.ADMIN.value()) {
            throw new ConfigException(ErrorCode.NoPrivilege);
        }
        context.put(Constants.ADMIN, Boolean.TRUE);
        context.next();
    }
}
