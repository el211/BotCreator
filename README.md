Description:

If you ever find yourself unable to test your server,
then this tool is for you! With this tool, you can
join bots to your server, and you can make them execute
commands on your behalf. As an example, you can join a
minigame and then make 1 bot or 15 to join along with you.

## Desktop / CLI usage

1. Download the BotCreator desktop JAR.
2. Double-click it or run `java -jar BotCreator.jar`.
   - `-host` or `-h`: host name. Default `127.0.0.1`.
   - `-port` or `-p`: port. Default `25565`.
   - `-clients` or `-c`: number of clients. Default `1`.
   - `-version` or `-v`: bot protocol version. Default latest.
3. Turn the target server to offline mode.
4. Connect the bots and run the commands/messages you need.

## Paper plugin

The `bot-creator-paper` module runs BotCreator clients continuously from a Paper
server, so a desktop PC does not need to remain online during long tests.

Each file under `plugins/BotCreator/servers/*.yml` represents one target server.
The filename is its id. For example, `server1.yml`:

```yaml
enabled: true
ip: "127.0.0.1"
port: 25565
version: "26.3"

local-server: true
godmode: true

botnames:
  - Lucy
  - Steve

generated-names:
  enabled: false
  prefix: "Bot_"
  start: 1
  amount: 100

join-delay-ms: 250
auto-reconnect: true
retry-delay-ms: 5000
connect-on-startup: true
```

Add as many files as needed: `server1.yml`, `server2.yml`,
`stress-test.yml`, and so on.

Commands:

```text
/botcreator list
/botcreator reload
/botcreator status <server>
/botcreator connect <server> [bot|all]
/botcreator disconnect <server> [bot|all]
/botcreator command <server> <bot|all> <command...>
/botcreator chat <server> <bot|all> <message...>
```

`godmode: true` is true server-side invulnerability only when that target is the
same Paper server running this plugin (`local-server: true`). A client cannot
force a different remote Minecraft server to ignore damage because damage is
server-authoritative.

BotCreator's current authentication model is intended for offline-mode testing
servers, matching the original tool.
