package dansplugins.fiefs.commands;

import dansplugins.fiefs.commands.abs.FiefsCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import java.util.ArrayList;
import java.util.List;

/** Permission-filtered player and staff help, with independent eight-entry sections. */
public class HelpCommand extends FiefsCommand {
    private record Entry(String command, String description, String permission, boolean staff) { }
    private static final List<Entry> ENTRIES = List.of(
        row("help [page]", "Read command help.", "help"),
        row("list", "List your faction's fiefs.", "list"),
        row("join <fief>", "Join a fief that invited you.", "join"),
        row("info [fief]", "Inspect a fief.", "info"),
        row("members [fief]", "List a fief's members.", "members"),
        row("leave", "Leave your fief.", "leave"),
        row("checkclaim", "Inspect this chunk's fief.", "checkclaim"),
        row("create <name>", "Create a fief.", "create"),
        row("invite <player>", "Invite a member.", "invite"),
        row("disband", "Disband your fief.", "disband"),
        row("claim", "Claim this chunk for your fief.", "claim"),
        row("unclaim", "Release this chunk.", "unclaim"),
        row("desc <description>", "Set your fief's description.", "desc"),
        row("rename <name>", "Rename your fief.", "rename"),
        row("kick <player>", "Remove a fief member.", "kick"),
        row("transfer <player>", "Transfer your holding.", "transfer"),
        row("flags <show|set>", "Inspect or set your fief's flags.", "flags"),
        row("whois <player>", "Find a player's fief.", "whois"),
        row("heir <player>", "Name your heir.", "heir"),
        row("capital [set]", "Inspect or name your fief's seat.", "capital"),
        row("succession", "Inspect inheritance rules and heir.", "succession"),
        row("grant <fief> <player>", "Grant a faction fief as faction head.", "grant"),
        row("revoke <fief>", "Revoke a faction fief as faction head.", "revoke"),
        new Entry("config <show|set>", "Inspect or change plugin settings.", "fiefs.config", true));

    private static Entry row(String command, String description, String permission) {
        return new Entry(command, description, "fiefs." + permission, false);
    }
    public HelpCommand() { super(List.of("help"), List.of("fiefs.help")); }
    @Override public boolean execute(CommandSender sender) { return execute(sender, new String[0]); }
    public boolean hasStaff(CommandSender sender) {
        return ENTRIES.stream().anyMatch(e -> e.staff && sender.hasPermission(e.permission));
    }
    @Override public boolean execute(CommandSender sender, String[] args) {
        List<Entry> ordinary = ENTRIES.stream().filter(e -> !e.staff && sender.hasPermission(e.permission)).toList();
        List<Entry> staff = ENTRIES.stream().filter(e -> e.staff && sender.hasPermission(e.permission)).toList();
        int ordinaryPages = pages(ordinary.size()), staffPages = pages(staff.size());
        int total = ordinaryPages + staffPages;
        boolean directStaff = args.length > 0 && args[0].equalsIgnoreCase("staff");
        if (directStaff && staff.isEmpty()) {
            sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
            return false;
        }
        if (total == 0) {
            sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
            return false;
        }
        int requested = number(args, directStaff ? 1 : 0);
        int page = directStaff ? ordinaryPages + Math.clamp(requested, 1, staffPages) : Math.clamp(requested, 1, total);
        boolean isStaff = page > ordinaryPages;
        List<Entry> entries = isStaff ? staff : ordinary;
        int sectionPage = isStaff ? page - ordinaryPages : page;
        Component header = Component.text("---------- ", NamedTextColor.DARK_GRAY)
            .append(Component.text(isStaff ? "Fiefs Staff" : "Fiefs", NamedTextColor.GREEN));
        if (total > 1) header = header.append(Component.text(" (", NamedTextColor.DARK_GRAY))
            .append(Component.text(page + "/" + total, NamedTextColor.WHITE))
            .append(Component.text(")", NamedTextColor.DARK_GRAY));
        sender.sendMessage(header.append(Component.text(" ----------", NamedTextColor.DARK_GRAY)));
        entries.stream().skip((sectionPage - 1L) * 8).limit(8).forEach(e -> sender.sendMessage(
            Component.text("/fi " + e.command, NamedTextColor.GREEN)
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(e.description, NamedTextColor.GRAY))));
        sender.sendMessage(Component.text(page < total ? "---------- /fi help " + (page + 1) + " for more ----------"
            : "--------------------------------", NamedTextColor.DARK_GRAY));
        return true;
    }
    private static int pages(int size) { return (size + 7) / 8; }
    private static int number(String[] args, int index) {
        try { return args.length > index ? Integer.parseInt(args[index]) : 1; }
        catch (NumberFormatException invalid) { return 1; }
    }
}
