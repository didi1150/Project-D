package dev.bukkit.command;

import dev.core.entity.rpgclass.RPGClassType;
import me.kodysimpson.simpapi.command.SubCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.*;

public class CommandBuilderTest {

    private CommandSender sender = new SystemOutSender();

    @Mock
    private Player player;

    @BeforeEach
    void setUp() {
        player = Mockito.mock(Player.class);
    }

    @AfterEach
    void tearDown() {

    }

    @Test
    public void test() {
        //     -1     0        1           2
        //  /d item <give|save|update>
        //  /d item give <playerName> <itemName>
        //  /d item save <itemName>
        //  /d item update
        //  /d item <itemName>
        SubCommand subCommand = SubCommandBuilder.startBuilding("item")
                .setCommandAction(3, "give", (commandSender, args) -> System.out.println("gave " + args[1] + " a " + args[2]))
                .setCommandAction(2, "save", (commandSender, args) -> System.out.println("saved " + args[1]))
                .setCommandAction(1, "update", (commandSender, args) -> System.out.println("updated items"))
                .setCommandAction(1, (commandSender, args) -> System.out.println("equipped " + args[0]))
                .setCommandArgumentsList(0, List.of("give", "save", "update", "ASPECT OF THE DRAGON", "BONEMERANG"))
                .setCommandArgumentsList(1, "give", List.of("Player237", "Player174"), "playerName")
                .setCommandArgumentsList(2, "give", List.of("ASPECT OF THE DRAGON", "BONEMERANG"), "itemName")
                .setCommandArgumentsList(1, "save", List.of(), "itemName")
                .build();
        subCommand.perform(sender, new String[]{"item", "give", "Player237", "BONEMERANG"});
        subCommand.perform(sender, new String[]{"item", "give", "Player237", "asdasd"});
        subCommand.perform(sender, new String[]{"item", "give", "ashdasi", "BONEMERANG"});
        subCommand.perform(sender, new String[]{"item", "give", "Player237"});
        subCommand.perform(sender, new String[]{"item", "give"});
        subCommand.perform(sender, new String[]{"item", "save"});
        subCommand.perform(sender, new String[]{"item", "save", "ashdash"});

        subCommand.perform(sender, new String[]{"item", "update"});

        subCommand.perform(sender, new String[]{"item", "BONEMERANG"});

    }

    @Test
    public void commandManagerTest() {

        MainCommandBuilder.MainCommand m = MainCommandBuilder.startBuilding("project-d")
                .setDescription("Main command for Project-D")
                .setUsage("/project-d")
                .addAlias("d")
                .addSubCommand(
                        SubCommandBuilder.startBuilding("giveItem")
                                .setDescription("to give items")
                                .setCommandAction(1, (sender, args) -> {
                                    String id = args[0];
                                    sender.sendMessage("Success! You received " + id);
                                })
                                .addAlias("g")
                                .setCommandArgumentsList(0, List.of("ASPECT OF THE DRAGON", "BONEMERANG"), "itemName")
                )
                .addSubCommand(
                        SubCommandBuilder.startBuilding("showStats")
                                .setDescription("to see your stats")
                                .setCommandAction(0, (sender, args) -> {
                                    sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
                                    sender.sendMessage(ChatColor.LIGHT_PURPLE + "some stats");
                                    sender.sendMessage(ChatColor.GOLD + "═══════════════════════════════════");
                                })
                )
                .addSubCommand(
                        SubCommandBuilder.startBuilding("selectActive")
                                .setDescription("to select a class")
                                .setCommandAction(1, (sender, args) -> {
                                    sender.sendMessage("Set active class to " + args[0]);
                                })
                                .setCommandArgumentsList(0,
                                        Arrays.asList(RPGClassType.values()).stream()
                                                .filter(classType -> classType != RPGClassType.NONE)
                                                .map(Enum::name).toList()
                                        , "className"
                                )
                )
                .addSubCommand(
                        SubCommandBuilder.startBuilding("setXp")
                                .setDescription("to set your current xp of a class")
                                .setCommandAction(2, (sender, args) -> {
                                    try {
                                        RPGClassType targetClass = RPGClassType.valueOf(args[0]);
                                        int newXp = Integer.valueOf(args[1]);

                                        sender.sendMessage("Set " + targetClass.name() + "-XP to " + newXp);

                                    } catch (Exception e) {
                                        sender.sendMessage("/setXp <class> <xp>");
                                    }
                                })
                                .setCommandArgumentsList(0,
                                        Arrays.stream(RPGClassType.values())
                                                .filter(classType -> classType != RPGClassType.NONE)
                                                .map(Enum::name).toList()
                                        , "className"
                                )
                                .setCommandArgumentsList(1, "xpNumber(Integer)")
                )
                .addSubCommand(
                        SubCommandBuilder.startBuilding("something")
                )
                .build();

        CoreCommand coreCommand = new CoreCommand(m.name(), m.description(), m.usage(), m.commandList(), m.aliases(), new ArrayList<>(m.subCommands()));

        System.out.println("d: " + coreCommand.tabComplete(player, "", new String[]{""}));
        System.out.println("giveItem: " + coreCommand.tabComplete(player, "", new String[]{"giveItem", ""}));
        System.out.println("g: " + coreCommand.tabComplete(player, "", new String[]{"g", ""}));
        System.out.println("g BONEMERANG: " + coreCommand.tabComplete(player, "", new String[]{"g", "BONEMERANG", ""}));
        System.out.println("showStats: " + coreCommand.tabComplete(player, "", new String[]{"showStats", ""}));
        System.out.println("selectActive: " + coreCommand.tabComplete(player, "", new String[]{"selectActive", ""}));

        System.out.println();

        coreCommand.execute(sender, "", new String[]{});

        System.out.println();

        coreCommand.execute(sender, "", new String[]{"giveItem"});
        coreCommand.execute(sender, "", new String[]{"g"});
        coreCommand.execute(sender, "", new String[]{"g", "sadhasd"});
        coreCommand.execute(sender, "", new String[]{"g", "BONEMERANG"});
        coreCommand.execute(sender, "", new String[]{"g", "BONEMERANG", "sdadas"});

        System.out.println();

        coreCommand.execute(sender, "", new String[]{"showStats"});
        coreCommand.execute(sender, "", new String[]{"showStats", "sodja"});

        System.out.println();

        coreCommand.execute(sender, "", new String[]{"selectActive"});
        coreCommand.execute(sender, "", new String[]{"selectActive", "ajsda"});
        coreCommand.execute(sender, "", new String[]{"selectActive", "TANK"});

        System.out.println();

        coreCommand.execute(sender, "", new String[]{"setXp"});
        coreCommand.execute(sender, "", new String[]{"setXp", "ajsda"});
        coreCommand.execute(sender, "", new String[]{"setXp", "TANK"});
        coreCommand.execute(sender, "", new String[]{"setXp", "TANK", "10.5"});
        coreCommand.execute(sender, "", new String[]{"setXp", "TANK", "asd"});
        coreCommand.execute(sender, "", new String[]{"setXp", "TANK", "10"});

        System.out.println();

        coreCommand.execute(sender, "", new String[]{"something"});
    }

}
