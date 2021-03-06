package name.falgout.jeffrey.moneydance.venmoservice;

import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.ItemEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import com.infinitekind.moneydance.model.Account;
import com.infinitekind.moneydance.model.AccountUtil;
import com.infinitekind.moneydance.model.AcctFilter;
import com.moneydance.apps.md.controller.FeatureModule;
import com.moneydance.apps.md.controller.FeatureModuleContext;
import com.moneydance.apps.md.view.gui.MoneydanceGUI;
import com.moneydance.apps.md.view.gui.OnlineManager;
import com.moneydance.modules.features.venmoservice.Main;

import name.falgout.jeffrey.moneydance.venmoservice.rest.Auth;
import name.falgout.jeffrey.moneydance.venmoservice.rest.Me;
import name.falgout.jeffrey.moneydance.venmoservice.rest.URIBrowser;
import name.falgout.jeffrey.moneydance.venmoservice.rest.VenmoClient;

public class AccountSetup extends JFrame {
  private static final long serialVersionUID = -3239889646842222229L;

  static Optional<ImageIcon> getDevTokenImage() {
    try {
      return Optional.of(new ImageIcon(Toolkit.getDefaultToolkit()
          .createImage(Main.getResource(AccountSetup.class.getResourceAsStream("venmo.png")))));
    } catch (IOException e) {
      e.printStackTrace();
      return Optional.empty();
    }
  }

  private final VenmoAccountState state;
  private final FeatureModuleContext context;

  private final Auth auth;
  private final VenmoClient client;

  private final URI devAuth;

  private final DefaultComboBoxModel<Account> targetAccountModel;
  private final JComboBox<Account> targetAccount;

  private final JTextField token;
  private final JButton tokenLaunch;
  private final JButton devToken;

  private final JButton ok;
  private final JButton cancel;
  private final JButton clearData;

  public AccountSetup(VenmoAccountState state, FeatureModule feature,
      FeatureModuleContext context) {
    this.state = state;
    this.context = context;

    URIBrowser browser = new MoneydanceBrowser(context);
    auth = new Auth(browser);
    client = new VenmoClient();

    Auth auth = new Auth(browser, "2899");
    devAuth = auth.getAuthURI();
    auth.close();

    targetAccountModel = new DefaultComboBoxModel<>();
    targetAccount = new JComboBox<>();
    targetAccount.setModel(targetAccountModel);

    JLabel accountLabel = new JLabel("Target Account:");
    accountLabel.setLabelFor(accountLabel);

    token = new JTextField();
    token.setPreferredSize(new Dimension(200, 20));
    tokenLaunch = new JButton(new ImageIcon(feature.getIconImage()));
    tokenLaunch.setToolTipText("Open a token request in your Web browser.");
    devToken = getDevTokenImage().map(JButton::new).orElseGet(() -> new JButton("?"));

    JLabel tokenLabel = new JLabel("Access Token:");
    tokenLabel.setLabelFor(token);

    ok = new JButton("Download");
    cancel = new JButton("Cancel");
    clearData = new JButton("Clear Data");

    Box accountBox = new Box(BoxLayout.X_AXIS);
    accountBox.add(accountLabel);
    accountBox.add(targetAccount);

    Box tokenBox = new Box(BoxLayout.X_AXIS);
    tokenBox.add(tokenLabel);
    tokenBox.add(token);
    tokenBox.add(tokenLaunch);
    tokenBox.add(devToken);

    Box actions = new Box(BoxLayout.X_AXIS);
    actions.add(ok);
    actions.add(cancel);
    actions.add(clearData);

    Box content = new Box(BoxLayout.Y_AXIS);
    content.add(accountBox);
    content.add(tokenBox);
    content.add(actions);

    add(content);

    addWindowListener(new WindowAdapter() {
      @Override
      public void windowActivated(WindowEvent e) {
        updateAccounts();
      }
    });
    targetAccount.addItemListener(ie -> {
      if (ie.getStateChange() == ItemEvent.SELECTED) {
        Account a = (Account) ie.getItem();
        if (token.getText().isEmpty()) {
          state.getToken(a).ifPresent(this::setToken);
        }
      }
    });
    tokenLaunch.addActionListener(ae -> fetchToken());
    devToken.addActionListener(ae -> {
      try {
        browser.browse(devAuth);
      } catch (Exception e) {
        Main.getUI(context).showErrorMessage(e);
      }
    });
    ok.addActionListener(ae -> {
      Optional<String> token = getToken();
      if (token.isPresent()) {
        downloadTransactions(getTargetAccount(), token.get());
      } else {
        JOptionPane.showMessageDialog(this, "Please enter an access token.", "Error",
            JOptionPane.ERROR_MESSAGE);
      }
    });
    cancel.addActionListener(ae -> {
      dispose();
    });
    clearData.addActionListener(ae -> {
      try {
        state.removeFrom(context.getCurrentAccountBook().getLocalStorage());
      } catch (Exception e) {
        Main.getUI(context).showErrorMessage(e);
      }
    });
  }

  @Override
  public void dispose() {
    super.dispose();
    auth.close();
  }

  private void updateAccounts() {
    Account selected = (Account) targetAccount.getSelectedItem();

    List<Account> accounts = AccountUtil.allMatchesForSearch(context.getRootAccount(),
        AcctFilters.and(AcctFilter.ACTIVE_CATEGORY_CHOICE_FILTER, AcctFilter.NON_CATEGORY_FILTER));

    targetAccountModel.removeAllElements();
    for (Account a : accounts) {
      targetAccountModel.addElement(a);
    }

    if (selected == null || !accounts.contains(selected)) {
      findVenmoAccount(state, accounts).ifPresent(targetAccount::setSelectedItem);
    } else {
      targetAccount.setSelectedItem(selected);
    }

    pack();
  }

  private Optional<Account> findVenmoAccount(VenmoAccountState state, Iterable<Account> accounts) {
    if (state.getAccounts().isEmpty()) {
      for (Account a : accounts) {
        if (a.getFullAccountName().toUpperCase().contains("VENMO")) {
          return Optional.of(a);
        }
      }
    } else {
      return Optional.of(state.getAccounts().iterator().next());
    }

    return Optional.empty();
  }

  private CompletionStage<String> fetchToken() {
    CompletionStage<String> token = auth.authorize();
    return token.whenCompleteAsync((t, ex) -> {
      if (t != null) {
        setToken(t);
      }
      if (ex != null) {
        Main.getUI(context).showErrorMessage(ex);
      }
    } , SwingUtilities::invokeLater);
  }

  private void setToken(String token) {
    this.token.setText(token);
    this.token.setCaretPosition(0);
  }

  private Account getTargetAccount() {
    return targetAccount.getItemAt(targetAccount.getSelectedIndex());
  }

  private Optional<String> getToken() {
    return token.getText().isEmpty() ? Optional.empty() : Optional.of(token.getText());
  }

  private void downloadTransactions(Account account, String token) {
    MoneydanceGUI gui = Main.getUI(context);

    TransactionImporter worker = new TransactionImporter(client, token,
        state.getLastFetched(account).orElseGet(() -> getCreationDate(account)), account);
    worker.addPropertyChangeListener(pce -> {
      if (pce.getPropertyName().equals("state")) {
        if (pce.getNewValue().equals(SwingWorker.StateValue.STARTED)) {
          gui.setStatus("Downloading Venmo transactions to account " + account, -1);

          worker.getMe().thenApply(Me::getName).thenAcceptAsync(name -> {
            gui.setStatus("Downloading " + name + "'s Venmo transactions to account " + account,
                -1);
          } , SwingUtilities::invokeLater);
        } else if (pce.getNewValue().equals(SwingWorker.StateValue.DONE)) {
          gui.setStatus("", 0);

          try {
            ZonedDateTime fetched = worker.get(); // Check for an exception.

            new OnlineManager(gui).processDownloadedTxns(account);

            state.setToken(account, token);
            state.setLastFetched(account, fetched);
            state.save(context.getCurrentAccountBook().getLocalStorage());
          } catch (ExecutionException e) {
            gui.showErrorMessage(e.getCause());
            e.printStackTrace();
          } catch (Exception e) {
            gui.showErrorMessage(e);
          }
        }
      }
    });
    worker.execute();

    dispose();
  }

  private ZonedDateTime getCreationDate(Account account) {
    long creationDate = account.getCreationDate();
    ZonedDateTime dateTime = Instant.ofEpochMilli(creationDate).atZone(ZoneId.systemDefault());

    LocalDate date = dateTime.toLocalDate();
    return date.atStartOfDay(ZoneId.systemDefault());
  }
}
