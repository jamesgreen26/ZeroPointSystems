![ZPS Logo](https://cdn.modrinth.com/data/6sObdFxl/images/e91963d5ba8b3cd9eb26ec593b8d67efac9e3ef1.png)

**Zero Point Systems** is a technology and automation mod for Forge 1.20.1 and NeoForge 1.21.1, featuring equipment manufactured by the similarly named Zero Point Systems corporation. Its product catalog covers electrical infrastructure, industrial machinery, radio communications, programmable control systems, and enough metal plating to furnish a small orbital facility.

The mod includes:

* **Cable networks** for transporting multiple independent redstone signals and Forge Energy
* **Data cables, radios, displays, and logic blocks** for sending and processing text
* **Script terminals and serial buses** for controlling machines with a simple scripting language
* **Production machines** including a rolling mill, impact piston, sieve, assembler, and scriptable robotic arm
* **Gas networks** of ducts, compressors, and vaporizers, feeding fusion reactors and ship thrusters for Valkyrien Skies and Sable
* **Power generation, storage, and tools** such as the Coal Burner, Power Cell, Power Drill, and Chainsaw
* **Industrial construction materials** including plating, catwalks, trusses, lights, gratings, and hazard markings

A wiki documenting some of the features is available [here](https://jamesgreen26.github.io/ZeroPointSystems/). Other features are documented in-game via item tooltips, the [Ponder System](https://github.com/Creators-of-Create/Ponder), and with the [JEI mod](https://modrinth.com/mod/jei) (optional, must be installed alongside ZPS).

---

### Developer Information

ZPS does not have a dedicated Maven repository set up. To depend on ZPS, we recommend using Modrinth's Maven service:

```groovy
repositories {
    maven {
        name = "Modrinth"
        url = "https://api.modrinth.com/maven"
    }
}

dependencies {

    // replace <version> with a valid ZPS version from the Modrinth page, such as 1.21.1-2.6.4
    implementation "maven.modrinth:zps:<version>"
}
```

If you are making an addon for Zero Point Systems, we'd love to hear about it! Feel free to reach out via [GitHub Issues](https://github.com/jamesgreen26/ZeroPointSystems/issues) or [on Discord](https://discord.com/invite/pfeZCtBqwz) if you have any questions.
