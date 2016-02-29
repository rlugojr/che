package org.eclipse.che.ide.debug;

/**
 * @author Mykola Morhun
 */
public interface DebuggerObservable {

    /**
     * Adds new listener.
     */
    void addDebuggerObserver(DebuggerObserver debuggerObserver);

    /**
     * Removes existent listener.
     */
    void removeDebuggerObserver(DebuggerObserver debuggerObserver);

}
