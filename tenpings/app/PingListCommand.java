/*
 * Copyright 2020-present Open Networking Foundation
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
package org.tenpings.app;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;

/**
 * Sample Apache Karaf CLI command
 */
@Service
@Command(scope = "onos", name = "tenpings-list",
         description = "List the database of executed pings")
public class PingListCommand extends AbstractShellCommand {

    @Override
    protected void doExecute() {

        //This line is required to use the functions and the variables that are defined in AppComponent
        AppComponent appComponent = get(AppComponent.class);

        //TODO-1 modify this string for STEP 4
        print("--- Done TODO 1 Professor ! ---");
        print(appComponent.hostDatabaseToString());
    }
}
