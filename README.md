# Minecraft Forge 1.8.9 MDK

A Minecraft Forge mod development kit for Minecraft 1.8.9.

## Prerequisites

- **Java JDK 8** - Forge 1.8.9 requires Java 8. Newer versions will not work.
  - [Download Adoptium JDK 8](https://adoptium.net/temurin/releases/?version=8)
- **Git** (optional, for version control)

## Initial Setup

Get the correct Java JDK (1.8 OR 8)
Check on Mac:
```bash
/usr/libexec/java_home -V
export JAVA_HOME=$(/usr/libexec/java_home -v <version>)
```
On Windows set the JAVA_HOME environment variable to point to your JDK 8 installation.
Then set your PATH variable to include `%JAVA_HOME%\bin`.

Before importing into any IDE, you must run the Gradle setup:

```bash
# Windows
gradlew setupDecompWorkspace

# Linux/macOS
./gradlew setupDecompWorkspace
```

> **Note:** This process downloads and decompiles Minecraft, which may take several minutes.

---

## IDE Setup

### IntelliJ IDEA (Recommended)

1. **Import the project**
   - Open IntelliJ IDEA
   - Select `File` → `Open`
   - Navigate to the project folder and select `build.gradle`
   - Click `Open as Project`

2. **Generate run configurations**
   ```bash
   # Windows
   gradlew genIntellijRuns
   
   # Linux/macOS
   ./gradlew genIntellijRuns
   ```

3. **Refresh Gradle** (if needed)
   - Open the Gradle tool window (`View` → `Tool Windows` → `Gradle`)
   - Click the refresh icon

4. **Run the mod**
   - Use the generated run configurations in the top-right dropdown
   - Select `runClient` and click the green play button

---

### Eclipse

1. **Generate Eclipse files**
   ```bash
   # Windows
   gradlew eclipse
   
   # Linux/macOS
   ./gradlew eclipse
   ```

2. **Import the project**
   - Open Eclipse
   - Set your workspace to the `eclipse` folder inside the project directory
   - Select `File` → `Import` → `General` → `Existing Projects into Workspace`
   - Browse to the project root and import

3. **Run the mod**
   - Right-click the project → `Run As` → `Java Application`
   - Select `GradleStart` as the main class

---

### Visual Studio Code

1. **Install required extensions**
   - [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack)
   - [Gradle for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-gradle)

2. **Open the project**
   - Open VS Code
   - Select `File` → `Open Folder`
   - Select the project root folder

3. **Configure Java version**
   - Press `Ctrl+Shift+P` (or `Cmd+Shift+P` on macOS)
   - Type `Java: Configure Java Runtime`
   - Ensure JDK 8 is selected for this project

4. **Run the mod**
   - Open the Gradle sidebar (elephant icon)
   - Navigate to `Tasks` → `forgegradle` → `runClient`
   - Double-click to run

---

## Troubleshooting

### Missing dependencies
```bash
gradlew --refresh-dependencies
```

### Reset everything
```bash
gradlew clean
gradlew setupDecompWorkspace
```

### Wrong Java version
Ensure `JAVA_HOME` points to JDK 8:
```bash
# Check current version
java -version

# Should output something like:
# openjdk version "1.8.0_xxx"
```

---

## Project Structure

```
├── src/main/java/          # Your mod source code
├── src/main/resources/     # Assets and mod metadata
├── build.gradle            # Build configuration
├── eclipse/                # Eclipse workspace
└── run/                    # Game run directory
```

## Building

To build a distributable JAR:
```bash
gradlew build
```

The output JAR will be in `build/libs/`.

## Resources

- [Forge Forums](http://www.minecraftforge.net/forum/)
- [Forge Documentation](https://mcforge.readthedocs.io/)
- [#ForgeGradle on EsperNet](irc://irc.esper.net/ForgeGradle)
