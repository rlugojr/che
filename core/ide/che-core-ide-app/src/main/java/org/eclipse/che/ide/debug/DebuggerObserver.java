package org.eclipse.che.ide.debug;

/**
 * @author Mykola Morhun
 */
public interface DebuggerObserver {

    void onDebuggerConnected(String host, int port);

    void onDebuggerConnectError(String host, int port);

    void onDebuggerDisconnected(String host, int port);

    void onBreakpointAdded();

    void onBreakpointDeleted();

    void onDeleteAllBreakpoints();

    void onStepInto();

    void onStepOver();

    void onStepOut();

    void onResume();

}
