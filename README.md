# PETGraphDB (VLDB 2026)

PETGraphDB is a temporal graph database system designed for managing and querying time-evolving graph data.  
This repository includes the PETGraphDB codebase (derived from Neo4j 4.4) and its custom temporal storage engine.

## Reproducible Build Guide for PETGraphDB

This guide documents how to bootstrap from source, package all required modules into the local Maven repository.

### Source Repositories

- `temporal-storage`: custom temporal storage engine module used by PETGraphDB.
- `temporal-neo4j-4.4`: PETGraphDB implementation built by modifying Neo4j 4.4, with `temporal-storage` as a dependency.
- `demo-test`: PETGraphDB benchmark and testing project, including dataset adapters, benchmark workloads, client/server adapters, and automation scripts for reproducible performance evaluation.

### 0. Toolchain Requirements

- **IntelliJ IDEA**: 2022.3.2 (Professional).
	- Newer versions such as 2025.1.1 may trigger compatibility issues.
- **Maven**: IntelliJ embedded Maven 3.8.1 (or equivalent command-line Maven).
- **JDK**: Java 11 (Oracle/OpenJDK 11 recommended).

### 1. Install the temporal-storage Module

Run the following command at the root directory of `temporal-storage`:

```bash
mvn -B clean install -DskipTests
```

This step cleans, compiles, packages, and installs the module into the local Maven repository while skipping tests.

### 2. Install the PETGraphDB Module

Run the following command at the root directory of `temporal-neo4j-4.4`:

```bash
mvn -B clean install -DskipTests -Dcheckstyle.skip -Dlicense.skip=true -Dlicensing.skip=true -Doverwrite
```

The additional `-D` flags bypass Neo4j-specific style/license validations that are not required for local packaging reproducibility.

### 3. Configure IntelliJ IDEA (Java and Scala)

1. **Set project JDK to Java 11**:  
	 `File -> Settings -> Project Structure -> Project -> SDK = Java 11`.

2. **Install and configure Scala SDK 2.12.13**:
	 - Install the Scala plugin from IDEA marketplace.
	 - Restart IDEA.
	 - Configure global Scala library:  
		 `File -> Settings -> Project Structure -> Global Library -> + -> Scala SDK -> 2.12.13`.

3. **Import `temporal-storage` as an IDEA module (recommended)**:
	 - If omitted, IDEA can still resolve the installed artifact from local Maven.
	 - If included as a module, direct source-level debugging across storage and PETGraphDB code becomes possible.