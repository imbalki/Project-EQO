// Origin: RikkaApps/Shizuku-API @ a27f6e4151ba7b39965ca47edb2bf0aeed7102e5, path: aidl/src/main/aidl/moe/shizuku/server/IShizukuServiceConnection.aidl
package moe.shizuku.server;

interface IShizukuServiceConnection {

    oneway void connected(IBinder service);

    oneway void died();
}
