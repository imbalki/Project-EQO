// Origin: RikkaApps/Shizuku-API @ a27f6e4151ba7b39965ca47edb2bf0aeed7102e5, path: aidl/src/main/aidl/moe/shizuku/server/IShizukuApplication.aidl
package moe.shizuku.server;

interface IShizukuApplication {

    oneway void bindApplication(in Bundle data) = 1;

    oneway void dispatchRequestPermissionResult(int requestCode, in Bundle data) = 2;

    // EQO: ask the attached app to render its own confirmation, without blocking the server.
    oneway void showPermissionConfirmation(int requestUid, int requestPid, in String requestPackageName, int requestCode) = 10000;
}