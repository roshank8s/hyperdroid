# HyperDroid

Run full Linux virtual machines natively on Android using the [Android Virtualization Framework (AVF)](https://source.android.com/docs/core/virtualization) with KVM hardware acceleration. No root required.

## How It Works

HyperDroid leverages Android's built-in virtualization support (crosvm + KVM) that ships on Pixel and other supported devices. Since the `VirtualMachineManager` API is `@SystemApi`, HyperDroid uses [Shizuku](https://shizuku.rikka.app/) to grant the `MANAGE_VIRTUAL_MACHINE` permission without requiring root access.

**Architecture:** Kotlin + Jetpack Compose + Hilt + Room + MVVM

## Features

- **VM Creation** -- Configure name, CPU cores (1-8), RAM (512-8192 MB), disk size (4-128 GB), and networking
- **VM Lifecycle** -- Start, stop, and delete virtual machines with real-time status tracking
- **SSH Terminal** -- Built-in terminal with automatic SSH connection to running VMs (auto-discovers VM IP via network scan)
- **Shortcut Key Bar** -- Scrollable bar with Ctrl, ^C, ^D, ^Z, ^L, Esc, Tab, arrow keys, and special characters (/, -, |, ~, _, .)
- **Ctrl Combo Mode** -- Tap Ctrl then type any letter to send control characters
- **Foreground Service** -- Keeps VMs running in the background with an ongoing notification
- **Guided Setup Wizard** -- 7-step wizard that walks through enabling Developer Mode, Wireless Debugging, Shizuku, and granting VM permissions
- **Auto-Detection** -- Polls device status every 2 seconds and auto-advances setup steps as requirements are met
- **OS Image Management** -- Download and verify OS images with SHA-256 checksum validation
- **Cloud-Init Support** -- Uses cidata ISO with cloud-init for automatic VM configuration (network, SSH, users)
- **Theme Support** -- Light, dark, and system-follow theme options

## Requirements

- **Device:** ARM64 device with AVF support (Pixel 7+, Pixel Tablet, etc.)
- **Android:** 13+ (API 33+)
- **Shizuku:** Required for granting VM permission without root

## Setup

### 1. Install Shizuku

Download [Shizuku](https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api) from the Play Store.

### 2. Enable Developer Options

Go to **Settings > About phone** and tap **Build number** 7 times.

### 3. Enable Wireless Debugging

Go to **Settings > Developer options > Wireless debugging** and enable it.

### 4. Start Shizuku

Open Shizuku and start it via the **Wireless debugging** pairing method:
1. In Shizuku, tap "Start via Wireless debugging"
2. Open the notification shade and tap the Wireless debugging notification
3. Tap "Pair device" and enter the pairing code in Shizuku

### 5. Launch HyperDroid

The setup wizard will detect each step automatically and guide you through granting the `MANAGE_VIRTUAL_MACHINE` permission via Shizuku.

## VM Image Setup

HyperDroid uses Debian genericcloud ARM64 images with cloud-init for automatic SSH configuration.

### Included Files

The `vms/` directory contains:

| File | Size | Description |
|------|------|-------------|
| `debian-13-genericcloud-arm64.tar.xz` | 199 MB | Compressed Debian 13 (Trixie) genericcloud ARM64 disk image |
| `cidata.iso` | 57 KB | Cloud-init NoCloud datasource ISO with SSH + network config |

### Preparing the Image

1. Extract the disk image:
   ```bash
   cd vms/
   xz -d debian-13-genericcloud-arm64.tar.xz
   tar xf debian-13-genericcloud-arm64.tar
   # Produces: disk.raw (3 GB)
   ```

2. Push to device:
   ```bash
   adb push disk.raw /data/local/tmp/debian-13-nocloud-arm64.img
   adb push cidata.iso /data/local/tmp/cidata.iso

   # Move to app's internal storage
   adb shell run-as com.hyperdroid cp /data/local/tmp/debian-13-nocloud-arm64.img \
     /data/user/0/com.hyperdroid/files/vm_images/debian-13-nocloud-arm64.img
   adb shell run-as com.hyperdroid cp /data/local/tmp/cidata.iso \
     /data/user/0/com.hyperdroid/files/vm_images/cidata.iso

   # Cleanup temp files
   adb shell rm /data/local/tmp/debian-13-nocloud-arm64.img
   adb shell rm /data/local/tmp/cidata.iso
   ```

### Cloud-Init Configuration (cidata.iso)

The included `cidata.iso` contains:

**user-data:**
- Root password: `hyperdroid`
- SSH enabled with password authentication
- `PermitRootLogin yes`

**network-config:**
- DHCP on all `en*` and `eth*` interfaces (netplan v2)

**meta-data:**
- Instance ID: `hyperdroid-vm-001`
- Hostname: `hyperdroid-vm`

To create a custom cidata ISO:
```bash
# Create the config files
mkdir cidata
cat > cidata/user-data << 'EOF'
#cloud-config
password: your-password
chpasswd: { expire: false }
ssh_pwauth: true
runcmd:
  - sed -i 's/^#\?PermitRootLogin.*/PermitRootLogin yes/' /etc/ssh/sshd_config
  - systemctl restart sshd
EOF

cat > cidata/meta-data << 'EOF'
instance-id: my-vm
local-hostname: my-vm
EOF

cat > cidata/network-config << 'EOF'
version: 2
ethernets:
  id0:
    match:
      name: "en*"
    dhcp4: true
  id1:
    match:
      name: "eth*"
    dhcp4: true
EOF

# Generate ISO (requires genisoimage or mkisofs)
genisoimage -output cidata.iso -volid cidata -joliet -rock \
  cidata/user-data cidata/meta-data cidata/network-config
```

## SSH Connection

When you open the Terminal screen for a running VM, HyperDroid:

1. Tries AVF console PFD first (only works on debuggable/eng builds)
2. Falls back to SSH with automatic VM IP discovery
3. Scans the AVF network subnet (`avf_tap_fixed` interface) for hosts with SSH port 22 open
4. Connects using JSch with credentials: `root` / `hyperdroid`
5. Opens an interactive xterm PTY (120x40)

The terminal includes a shortcut bar for keys that are difficult to type on mobile:

| Key | Action |
|-----|--------|
| **Ctrl** | Toggle -- next letter typed sends Ctrl+letter |
| **^C** | Send SIGINT (interrupt) |
| **^D** | Send EOF (logout) |
| **^Z** | Send SIGTSTP (suspend) |
| **^L** | Clear screen |
| **Esc** | Escape key |
| **Tab** | Tab completion |
| **Arrow keys** | Command history / cursor movement |
| **/ - \| ~ _ .** | Insert character at cursor |

## Project Structure

```
app/src/main/java/com/hyperdroid/
├── HyperDroidApp.kt              # Hilt application class
├── MainActivity.kt                # Single-activity Compose host
├── MainViewModel.kt               # Root navigation state
│
├── navigation/
│   └── HyperDroidNavGraph.kt      # Type-safe nav routes (@Serializable)
│
├── model/
│   ├── VMConfig.kt                # Room entity: VM configuration
│   ├── VMStatus.kt                # STOPPED, STARTING, RUNNING, PAUSED, ERROR
│   ├── OSType.kt                  # Debian, Ubuntu, Alpine, Fedora, Arch, Custom
│   ├── OSImage.kt                 # OS image metadata
│   └── PermissionStatus.kt        # Setup wizard states
│
├── db/
│   ├── AppDatabase.kt             # Room database (version 1)
│   ├── VMDao.kt                   # CRUD operations for VMs
│   └── Converters.kt              # Room type converters
│
├── data/
│   ├── VMRepository.kt            # VM data access layer
│   ├── ImageRepository.kt         # OS image download + SHA-256 validation
│   └── PreferencesManager.kt      # DataStore preferences
│
├── di/
│   ├── AppModule.kt               # Application-level DI
│   ├── DatabaseModule.kt          # Room database DI
│   └── VMModule.kt                # VMEngine + service DI
│
├── vm/
│   └── VMEngine.kt                # AVF reflection wrapper (VirtualMachineManager)
│
├── service/
│   ├── VMService.kt               # Foreground service for running VMs
│   └── VMServiceManager.kt        # Service start/stop helper
│
├── permission/
│   ├── AVFChecker.kt              # AVF/KVM support detection via reflection
│   ├── ShizukuHelper.kt           # Shizuku permission grant (IPackageManager + shell)
│   └── PermissionManager.kt       # Permission state orchestrator
│
├── ui/
│   ├── setup/                     # 7-step setup wizard
│   │   ├── SetupWizardScreen.kt
│   │   ├── SetupViewModel.kt
│   │   ├── components/            # StepIndicator, SetupStepLayout
│   │   └── steps/                 # Welcome, DeveloperMode, WirelessDebugging,
│   │                              # InstallShizuku, StartShizuku, GrantPermission,
│   │                              # SetupComplete
│   ├── home/
│   │   ├── HomeScreen.kt          # VM list + device info
│   │   ├── HomeViewModel.kt       # VM operations
│   │   └── VMCard.kt              # VM card with status chip + actions
│   ├── createvm/
│   │   ├── CreateVMScreen.kt      # VM creation form
│   │   └── CreateVMViewModel.kt
│   ├── terminal/
│   │   ├── TerminalScreen.kt      # Dark console UI + shortcut bar
│   │   └── TerminalViewModel.kt   # SSH/console connection
│   ├── settings/
│   │   ├── SettingsScreen.kt      # Theme selector + re-run setup
│   │   └── SettingsViewModel.kt
│   └── theme/                     # Material 3 theming
│
└── util/
    └── DeviceUtils.kt             # Device info helpers
```

## Tech Stack

| Component | Library | Version |
|-----------|---------|---------|
| Language | Kotlin | 2.1.0 |
| UI | Jetpack Compose (Material 3) | BOM 2024.12.01 |
| DI | Hilt | 2.54 |
| Database | Room | 2.6.1 |
| Navigation | Navigation Compose | 2.8.5 |
| Preferences | DataStore | 1.1.2 |
| SSH | JSch (mwiede fork) | 0.2.21 |
| Permission | Shizuku | 13.1.5 |
| Reflection | HiddenApiBypass | 6.1 |
| Build | AGP | 8.7.3 |
| Annotation Processing | KSP | 2.1.0-1.0.29 |

## Building

```bash
# Debug build
./gradlew assembleDebug

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**Requirements:** JDK 17, Android SDK 35, NDK (for ARM64 filtering)

## Permissions

| Permission | Purpose |
|------------|---------|
| `MANAGE_VIRTUAL_MACHINE` | Create and manage AVF virtual machines |
| `USE_CUSTOM_VIRTUAL_MACHINE` | Custom VM configurations |
| `INTERNET` | Network access for VMs and image downloads |
| `ACCESS_NETWORK_STATE` | VM IP discovery via network interface scan |
| `FOREGROUND_SERVICE` | Keep VMs running in background |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Foreground service type for VM hosting |
| `POST_NOTIFICATIONS` | VM status notifications |

## Roadmap

- **Phase 3:** File sharing (virtio-9p), snapshots, multiple OS support (Ubuntu, Alpine)
- **Phase 4:** GUI display (virtio-gpu), audio passthrough, USB passthrough
- **Phase 5:** Beta testing, Play Store release

## Supported Devices

Tested on:
- Pixel 7 (Android 16)

Should work on any ARM64 device with AVF support:
- Pixel 7 / 7 Pro / 7a
- Pixel 8 / 8 Pro / 8a
- Pixel 9 / 9 Pro / 9 Pro XL
- Pixel Fold / Pixel Tablet
- Other devices with `android.software.virtualization_framework` system feature

## License

MIT
