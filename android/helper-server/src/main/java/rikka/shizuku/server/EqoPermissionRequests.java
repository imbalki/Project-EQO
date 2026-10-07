package rikka.shizuku.server;

/** EQO-authored: one expiring, process-bound consent request; never an arbitrary UID grant. */
public final class EqoPermissionRequests {
    private int uid = -1;
    private int pid;
    private int code;
    private long expiresAt;

    public synchronized boolean begin(int uid, int pid, int code, long now) {
        if (this.uid != -1 && now < expiresAt) return false;
        this.uid = uid;
        this.pid = pid;
        this.code = code;
        expiresAt = now + 60_000;
        return true;
    }

    public synchronized void cancel(int callerUid, int callerPid) {
        if (uid == callerUid && pid == callerPid) uid = -1;
    }

    public synchronized boolean consume(int callerUid, int requestUid, int requestPid, int requestCode, long now) {
        if (callerUid != uid || requestUid != uid || requestPid != pid || requestCode != code) return false;
        boolean valid = now < expiresAt;
        uid = -1;
        return valid;
    }
}
