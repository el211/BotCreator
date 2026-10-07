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

Each file under `plugins/BotCreator/servers/*.yml` represents one isolated target server.
On a fresh install, BotCreator creates `server1.yml`, `server2.yml`, and
`server3.yml`. Each defaults to 250 generated bots with a different name
prefix and a different local port so the three bot pools do not get mixed.

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
  enabled: true
  prefix: "S1Bot_"
  start: 1
  amount: 250

join-delay-ms: 250
auto-reconnect: true
reconnect-immediately: true
retry-delay-ms: 5000
connect-on-startup: false
```

The three defaults use separate pools:
- `server1.yml`: `S1Bot_1` through `S1Bot_250`, port `25565`
- `server2.yml`: `S2Bot_1` through `S2Bot_250`, port `25566`
- `server3.yml`: `S3Bot_1` through `S3Bot_250`, port `25567`

Commands always target a server id, so `/botcreator connect server1 all` only
connects the server1 pool. Add more YAML files if more isolated targets are needed.

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

### Connection throttling during large tests

Paper's `bukkit.yml` defaults to a per-IP connection throttle. Because all BotCreator clients from one controller normally share the same source IP, a small `join-delay-ms` such as 250 ms can cause most bots to be kicked with `Connection throttled! Please wait before reconnecting.`

For an isolated load-test server, disable that throttle on the target and restart it:

```yaml
settings:
  connection-throttle: 0
```

If the target must keep connection throttling enabled, set `join-delay-ms` to at least the target throttle value instead.

`reconnect-immediately: true` makes an established bot attempt to reconnect as soon as a drop is detected. If that reconnect fails, `retry-delay-ms` is used as a backoff before trying again.\n\n`godmode: true` is true server-side invulnerability only when that target is the
same Paper server running this plugin (`local-server: true`). A client cannot
force a different remote Minecraft server to ignore damage because damage is
server-authoritative.

BotCreator's current authentication model is intended for offline-mode testing
servers, matching the original tool.
