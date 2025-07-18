/*
 * © Copyright Serdar Basegmez. 2015
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.openntf.xlogback.console;

import com.ibm.dots.task.ServerConsole;

public class DotsConsoleLogger implements IConsoleLogger {

    private transient ServerConsole console;

    public DotsConsoleLogger() {

    }

    private ServerConsole getConsole() {
        if (null == console) {
            console = new ServerConsole("");
        }

        return console;
    }

    @Override
    public void logMessage(final String message) {
        try {
			if (null != message) {
				getConsole().logMessage(message.replace("%", "%%"));
			}
        } catch (Throwable t) {
            // DOTS platform has not been launched yet. Falling back.
            System.out.println(message);
        }
    }

}
