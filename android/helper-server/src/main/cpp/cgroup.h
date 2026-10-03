// Origin: RikkaApps/Shizuku @ b844bc491f1790c72328e1a8e5b2349f8978f0ea, path: manager/src/main/jni/cgroup.h
#ifndef CGROUP_H
#define CGROUP_H

namespace cgroup {
    bool switch_cgroup(const char *cgroup, int pid);
}

#endif // CGROUP_H
