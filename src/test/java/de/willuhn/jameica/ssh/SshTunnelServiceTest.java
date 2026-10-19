/**********************************************************************
 *
 * Copyright (c) 2026 Olaf Willuhn
 * All rights reserved.
 * 
 * This software is copyrighted work licensed under the terms of the
 * Jameica License.  Please consult the file "LICENSE" for details. 
 *
 **********************************************************************/

package de.willuhn.jameica.ssh;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import de.willuhn.util.Settings;

/**
 * Tests des {@link SshTunnelService}.
 * 
 * <p>Der eigentliche Verbindungsaufbau benoetigt einen SSH-Server und wird
 * hier nicht getestet. Geprueft wird die Abbildung zwischen
 * {@link SshTunnelConfig} und den persistierten {@link Settings} sowie die
 * Verwaltung der Geheimnisse. Dazu wird dem Service ein auf einem
 * temporaeren Verzeichnis basierendes {@link Settings} injiziert und der
 * Tunnel-Aufbau ausgehebelt, damit kein laufender Jameica-Kontext benoetigt
 * wird.
 */
public class SshTunnelServiceTest
{
  private File configDir;
  private Settings settings;
  private TestService service;

  /**
   * Legt ein frisches, leeres Einstellungs-Verzeichnis samt Service an.
   * @throws Exception wenn das Verzeichnis nicht angelegt werden konnte.
   */
  @Before
  public void setUp() throws Exception
  {
    this.configDir = new File(System.getProperty("java.io.tmpdir"),"ssh-tunnel-service-test-" + System.nanoTime());
    this.configDir.mkdirs();

    this.settings = new Settings("cfg",this.configDir.getAbsolutePath(),SshTunnelService.class);
    this.service  = new TestService(this.settings);
  }

  /**
   * Entfernt das temporaere Verzeichnis wieder.
   */
  @After
  public void tearDown()
  {
    deleteRecursive(this.configDir);
  }

  /**
   * Ohne gespeicherte Werte muss {@link SshTunnelService#getConfig()} die
   * dokumentierten Defaults liefern.
   */
  @Test
  public void defaults()
  {
    SshTunnelConfig c = this.service.getConfig();

    Assert.assertFalse(c.isEnabled());
    Assert.assertNull(c.getSshHost());
    Assert.assertEquals(22,c.getSshPort());
    Assert.assertNull(c.getSshUser());
    Assert.assertEquals(SshTunnelConfig.AuthType.PASSWORD,c.getAuthType());
    Assert.assertNull(c.getPassword());
    Assert.assertNull(c.getPassphrase());
    Assert.assertEquals("/tmp/known_hosts",c.getKnownHosts());
    Assert.assertTrue(c.isStrictHostKeyChecking());
    Assert.assertEquals(0,c.getLocalPort());
    Assert.assertEquals("127.0.0.1",c.getRemoteHost());
    Assert.assertEquals(3306,c.getRemotePort());
    Assert.assertEquals(15000,c.getTimeout());
  }

  /**
   * {@code null} darf nicht gespeichert werden und nichts veraendern.
   */
  @Test
  public void saveNullIsIgnored() throws Exception
  {
    this.service.save(null);

    Assert.assertEquals(22,this.service.getConfig().getSshPort());
    Assert.assertEquals(0,this.service.started);
  }

  /**
   * Alle Felder muessen den Speicher-/Lese-Zyklus unveraendert ueberstehen.
   */
  @Test
  public void saveAndLoadRoundTrip() throws Exception
  {
    SshTunnelConfig c = new SshTunnelConfig();
    c.setEnabled(false);
    c.setSshHost("ssh.example.org");
    c.setSshPort(2222);
    c.setSshUser("alice");
    c.setAuthType(SshTunnelConfig.AuthType.PUBLICKEY);
    c.setPrivateKey("/home/alice/.ssh/id_ed25519");
    c.setPassword("secret");
    c.setPassphrase("phrase");
    c.setKnownHosts("/etc/ssh/known_hosts");
    c.setStrictHostKeyChecking(false);
    c.setLocalPort(12345);
    c.setRemoteHost("db.internal");
    c.setRemotePort(5432);
    c.setTimeout(7000);

    this.service.save(c);

    SshTunnelConfig l = this.service.getConfig();
    Assert.assertFalse(l.isEnabled());
    Assert.assertEquals("ssh.example.org",l.getSshHost());
    Assert.assertEquals(2222,l.getSshPort());
    Assert.assertEquals("alice",l.getSshUser());
    Assert.assertEquals(SshTunnelConfig.AuthType.PUBLICKEY,l.getAuthType());
    Assert.assertEquals("/home/alice/.ssh/id_ed25519",l.getPrivateKey());
    Assert.assertEquals("secret",l.getPassword());
    Assert.assertEquals("phrase",l.getPassphrase());
    Assert.assertEquals("/etc/ssh/known_hosts",l.getKnownHosts());
    Assert.assertFalse(l.isStrictHostKeyChecking());
    Assert.assertEquals(12345,l.getLocalPort());
    Assert.assertEquals("db.internal",l.getRemoteHost());
    Assert.assertEquals(5432,l.getRemotePort());
    Assert.assertEquals(7000,l.getTimeout());
  }

  /**
   * Das Auth-Verfahren muss als klein geschriebener Name persistiert werden,
   * damit {@link SshTunnelConfig.AuthType#parse(String)} ihn wieder lesen kann.
   */
  @Test
  public void authTypeIsStoredLowercase() throws Exception
  {
    SshTunnelConfig c = new SshTunnelConfig();
    c.setAuthType(SshTunnelConfig.AuthType.PUBLICKEY);
    this.service.save(c);

    Assert.assertEquals("publickey",this.settings.getString("ssh.auth",null));
    Assert.assertEquals(SshTunnelConfig.AuthType.PUBLICKEY,this.service.getConfig().getAuthType());
  }

  /**
   * Ein leerer Wert muss ein zuvor gespeichertes Geheimnis wieder loeschen.
   */
  @Test
  public void emptySecretClearsStoredValue() throws Exception
  {
    SshTunnelConfig c = new SshTunnelConfig();
    c.setPassword("secret");
    c.setPassphrase("phrase");
    this.service.save(c);
    Assert.assertEquals("secret",this.service.getConfig().getPassword());
    Assert.assertEquals("phrase",this.service.getConfig().getPassphrase());

    c.setPassword("");
    c.setPassphrase(null);
    this.service.save(c);
    Assert.assertNull(this.service.getConfig().getPassword());
    Assert.assertNull(this.service.getConfig().getPassphrase());
  }

  /**
   * Beim Speichern muss der Tunnel neu aufgebaut werden, sofern er aktiviert
   * ist. Bei deaktivierter Konfiguration darf kein Aufbau erfolgen.
   */
  @Test
  public void enabledTriggersRestart() throws Exception
  {
    SshTunnelConfig c = new SshTunnelConfig();
    c.setEnabled(true);
    this.service.save(c);

    Assert.assertTrue(this.service.isEnabled());
    Assert.assertEquals(1,this.service.started);
    Assert.assertEquals(1,this.service.stopped);

    c.setEnabled(false);
    this.service.save(c);

    Assert.assertFalse(this.service.isEnabled());
    Assert.assertEquals(1,this.service.started);
  }

  /**
   * Ohne aktiven Tunnel darf keine Verbindung gemeldet und kein lokaler Port
   * geliefert werden. {@link SshTunnelService#stop()} muss gefahrlos sein.
   */
  @Test
  public void stateWithoutTunnel()
  {
    SshTunnelService s = new SshTunnelService(this.settings);

    Assert.assertFalse(s.isRunning());
    Assert.assertEquals(-1,s.getLocalPort());

    s.stop();
    Assert.assertFalse(s.isRunning());
    Assert.assertEquals(-1,s.getLocalPort());
  }

  /**
   * Die Einstellungen muessen auf Platte persistiert und von einer neuen
   * Instanz wieder gelesen werden.
   */
  @Test
  public void settingsArePersisted() throws Exception
  {
    SshTunnelConfig c = new SshTunnelConfig();
    c.setSshHost("ssh.example.org");
    c.setRemotePort(5432);
    this.service.save(c);

    Settings reloaded = new Settings("cfg",this.configDir.getAbsolutePath(),SshTunnelService.class);
    SshTunnelService s2 = new TestService(reloaded);

    Assert.assertEquals("ssh.example.org",s2.getConfig().getSshHost());
    Assert.assertEquals(5432,s2.getConfig().getRemotePort());
  }

  /**
   * Loescht ein Verzeichnis rekursiv.
   * @param f das zu loeschende Verzeichnis oder File.
   */
  private static void deleteRecursive(File f)
  {
    if (f == null || !f.exists())
      return;

    File[] children = f.listFiles();
    if (children != null)
    {
      for (File child : children)
        deleteRecursive(child);
    }
    f.delete();
  }

  /**
   * Test-Service, der auf einem temporaeren {@link Settings} arbeitet, die
   * Geheimnisse in einer Map haelt und den Tunnel-Aufbau aushebelt.
   */
  private static class TestService extends SshTunnelService
  {
    private final Map<String,String> secrets = new HashMap<String,String>();

    int started = 0;
    int stopped = 0;

    /**
     * @param settings die zu verwendenden Einstellungen.
     */
    TestService(Settings settings)
    {
      super(settings);
    }

    /**
     * @see de.willuhn.jameica.ssh.SshTunnelService#getDefaultKnownHosts()
     */
    @Override
    String getDefaultKnownHosts()
    {
      return "/tmp/known_hosts";
    }

    /**
     * @see de.willuhn.jameica.ssh.SshTunnelService#getSecret(java.lang.String)
     */
    @Override
    String getSecret(String alias)
    {
      return this.secrets.get(alias);
    }

    /**
     * @see de.willuhn.jameica.ssh.SshTunnelService#setSecret(java.lang.String, java.lang.String)
     */
    @Override
    void setSecret(String alias, String value)
    {
      if (value == null || value.length() == 0)
        this.secrets.remove(alias);
      else
        this.secrets.put(alias,value);
    }

    /**
     * @see de.willuhn.jameica.ssh.SshTunnelService#start()
     */
    @Override
    public void start()
    {
      this.started++;
    }

    /**
     * @see de.willuhn.jameica.ssh.SshTunnelService#stop()
     */
    @Override
    public void stop()
    {
      this.stopped++;
    }
  }
}
