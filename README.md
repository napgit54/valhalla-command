# Valhalla Command (Fabric 26.2)

Command your Viking army. Feature 1: the war wheel.

- Hold **R** to open the wheel and let go to close it. Whatever section you are hovering is chosen.
- To change the key: Options → Controls → Key Binds → Valhalla Command.

## Getting the .jar
Push this project to GitHub. The `build` workflow runs automatically.
When it finishes, open Actions → the latest run → Artifacts and download it.
Use the jar that does **not** end in `-sources`.
Put it in your `mods` folder with Fabric API.

Look & feel settings (sizes, colours, animation speed, command names) are at the top of
`src/client/java/com/valhalla/command/client/CommandWheelScreen.java`.
