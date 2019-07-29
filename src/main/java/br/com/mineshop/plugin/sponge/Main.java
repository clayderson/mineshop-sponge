package br.com.mineshop.plugin.sponge;

import br.com.mineshop.msdk.MSDK;
import br.com.mineshop.msdk.exceptions.MsdkException;
import br.com.mineshop.msdk.exceptions.WebServiceException;
import br.com.mineshop.msdk.webservice.endpoints.v1.QueueItem;
import com.google.inject.Inject;
import ninja.leaping.configurate.commented.CommentedConfigurationNode;
import ninja.leaping.configurate.loader.ConfigurationLoader;
import org.slf4j.Logger;
import org.spongepowered.api.Game;
import org.spongepowered.api.Sponge;
import org.spongepowered.api.command.args.GenericArguments;
import org.spongepowered.api.command.spec.CommandSpec;
import org.spongepowered.api.config.DefaultConfig;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.game.state.GameStartedServerEvent;
import org.spongepowered.api.event.Listener;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.plugin.Plugin;
import org.spongepowered.api.scheduler.Task;
import org.spongepowered.api.text.Text;
import org.spongepowered.api.text.format.TextColors;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Plugin(id = "mineshop", name = "Mineshop", version = "1.0.0", description = "Visit https://mineshop.com.br")
public class Main {
  @Inject
  private Game game;

  @Inject
  Logger logger;

  @Inject
  @DefaultConfig(sharedRoot = true)
  private File configuration = null;

  @Inject
  @DefaultConfig(sharedRoot = true)
  ConfigurationLoader<CommentedConfigurationNode> configurationLoader = null;

  CommentedConfigurationNode configurationNode = null;

  private MSDK msdk = new MSDK();
  private Object plugin;

  @Listener
  public void onServerStart(GameStartedServerEvent event) {
    this.loadConfig();

    this.plugin = this;
    this.msdk.setCredentials(this.configurationNode.getNode("token").getString());

    CommandSpec updateTokenCommand = CommandSpec.builder()
    .description(Text.of("Define api token for use Mineshop API"))
    .arguments(GenericArguments.string(Text.of("token")))
    .executor(new UpdateTokenCommand(msdk, this))
    .build();

    game.getCommandManager().register(this, updateTokenCommand, "mineshop");

    int timerAfterRestart = this.configurationNode.getNode("eventLoop", "timer", "afterRestart").getInt();
    int timerDelay = this.configurationNode.getNode("eventLoop", "timer", "delay").getInt();

    if (timerAfterRestart < 20) {
      this.logger.warn(String.format(
        "O event loop está configurado para ser executado em %s segundo(s) logo após a " +
        "reinicialização do servidor ou do plugin! Recomendamos um delay entre 20 e 300 segundos neste campo.",
        Integer.toString(timerAfterRestart)
      ));
    }

    if (timerDelay < 10) {
      this.logger.warn(String.format(
        "O event loop está configurado para ser executado a cada %s segundo(s)! Recomendamos um " +
        "delay entre 10 e 60 segundos neste campo.",
        Integer.toString(timerDelay)
      ));
    }

    Task.Builder taskBuilder = Task.builder();
    taskBuilder.execute(() -> {
      QueueItem[] queueItems = null;

      try {
        queueItems = msdk.getQueueItems();
      } catch (WebServiceException | MsdkException e) {
        logger.warn(e.getMessage());
      }

      if (queueItems == null) {
        return;
      }

      for (QueueItem queueItem : queueItems) {
        Task.Builder taskBuilder1 = Task.builder();
        taskBuilder1.execute(() -> {
          if (queueItem.getType().equalsIgnoreCase("online")) {
            if (!Sponge.getServer().getPlayer(queueItem.getNickname()).isPresent()) {
              return;
            }

            Player player = Sponge.getServer().getPlayer(queueItem.getNickname()).get();

            int iterationCount = 0;
            int emptySlots = 0;

            for (Inventory i : player.getInventory().slots()) {
              if (iterationCount >= 36) {
                break;
              }

              iterationCount++;

              if (i.size() == 0) {
                emptySlots++;
              }
            }

            if (queueItem.getSlotsNeeded() > emptySlots) {
              player.sendMessage(Text.builder(String.format(
                "Não pudemos entregar todos os itens que você comprou em nossa loja porque seu " +
                "inventário não tem espaço suficiente. O restante dos itens serão entregues em %s segundo(s). " +
                "Para recebê-los, por favor, esvazie seu inventário.",
                Integer.toString(timerDelay)
              )).color(TextColors.LIGHT_PURPLE).build());

              return;
            }
          }

          try {
            msdk.hasBeenDelivered(queueItem.getNickname(), queueItem.getUuid());
            Sponge.getCommandManager().process(Sponge.getServer().getConsole(), queueItem.getCommand());
          } catch (WebServiceException | MsdkException e) {
            logger.warn(e.getMessage());
          }
        }).submit(plugin);
      }
    }).async().delay(timerAfterRestart, TimeUnit.SECONDS).interval(timerDelay, TimeUnit.SECONDS).submit(this);
  }

  private void loadConfig() {
    try {
      this.saveDefaultConfig();
      this.configurationNode = this.configurationLoader.load();
    } catch (IOException exception) {
      this.logger.error(String.format("Falha ao tentar carregar as configurações do plugin: %s", exception.getMessage()));
    }
  }

  private void saveDefaultConfig() throws IOException {
    if (this.configuration.exists()) {
      return;
    }

    if (!this.configuration.createNewFile()) {
      throw new IOException("Algo não saiu como esperado ao tentar criar aquivo de configurações");
    }

    this.configurationNode = this.configurationLoader.load();

    this.configurationNode.getNode("eventLoop", "timer", "afterRestart").setValue(60);
    this.configurationNode.getNode("eventLoop", "timer", "delay").setValue(20);
    this.configurationNode.getNode("token").setValue("");

    this.configurationLoader.save(this.configurationNode);
  }
}
