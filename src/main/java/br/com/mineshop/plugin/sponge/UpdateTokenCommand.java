package br.com.mineshop.plugin.sponge;

import br.com.mineshop.msdk.MSDK;
import org.spongepowered.api.command.CommandResult;
import org.spongepowered.api.command.CommandSource;
import org.spongepowered.api.command.args.CommandContext;
import org.spongepowered.api.command.spec.CommandExecutor;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import java.io.IOException;

public class UpdateTokenCommand implements CommandExecutor {
  private MSDK msdk;
  private Main plugin;

  UpdateTokenCommand(MSDK msdk, Main plugin) {
    this.msdk = msdk;
    this.plugin = plugin;
  }

  @Override
  public CommandResult execute(CommandSource src, CommandContext args) {
    if (src instanceof Player) {
      src.sendMessage(Text.builder("Este comando não pode ser executado fora do console do servidor").color(TextColors.RED).build());
      return CommandResult.empty();
    }

    String token = args.getOne("token").get().toString().trim().toLowerCase();

    this.msdk.setCredentials(token);
    this.plugin.configurationNode.getNode("token").setValue(token);

    try {
      this.plugin.configurationLoader.save(this.plugin.configurationNode);
    } catch (IOException e) {
      this.plugin.logger.error(e.getMessage());
    } finally {
      src.sendMessage(Text.builder(
        "Pronto! Se o token informado estiver correto, este servidor irá sincronizar com sua loja em " +
        "alguns instantes."
      ).color(TextColors.GREEN).build());
    }

    return CommandResult.success();
  }
}
