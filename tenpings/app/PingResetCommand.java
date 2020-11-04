package org.tenpings.app;

import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;

@Service
@Command(scope = "onos", name = "tenpings-reset",
        description = "Reset the database of executed pings")
public class PingResetCommand extends AbstractShellCommand {

    @Override
    protected void doExecute() {

        //TODO-2: Here you have to clear the pingInfoDatabase that is part of the AppComponent
        AppComponent appComponent = get(AppComponent.class);
        appComponent.pingInfoDatabase.clear();
    }
}
