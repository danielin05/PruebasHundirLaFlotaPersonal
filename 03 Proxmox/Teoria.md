<div style="display: flex; width: 100%;">
    <div style="flex: 1; padding: 0px;">
        <p>© Albert Palacios Jiménez, 2024</p>
    </div>
    <div style="flex: 1; padding: 0px; text-align: right;">
        <img src="./assets/ieti.png" height="32" alt="Logo de IETI" style="max-height: 32px;">
    </div>
</div>
<br/>

# Proxmox IETI  

Configurar el Proxmox per poder fer-hi funcionar servidors Java

Connectar-se al proxmox:

```bash
ssh -i id_rsa -p 20127 nomUsuari@ieticloudpro.ieti.cat
```

Suposant que 'id_rsa' és l'arxiu que té la clau privada d'accés al Proxmox

## Desinstal·lar apache

```bash
sudo systemctl stop apache2
sudo apt remove apache2
sudo apt purge apache2
sudo rm -rf /var/www/html
sudo apt autoremove
apache2 -v
```

- Ha de dir: *Command 'apache2' not found*

## Instal·lar Java MVN

```bash
sudo apt update
sudo apt install net-tools
sudo apt install openjdk-21-jre-headless
sudo apt install maven
mvn -v
```

- Ha de dir (semblant): Apache Maven 3.6.3

# Gestió amb scripts automàtics

Aquests scripts ajuden a fer tots els passos d'interacció amb Proxmox de manera senzilla

## Definir la configuració

Editar l'arxiu **./proxmox/config.env** segons la teva configuració

```txt
DEFAULT_USER="nomUsuari"
DEFAULT_RSA_PATH="$HOME/.ssh/id_rsa"
DEFAULT_SERVER_PORT="3000"
```

Executar els arxius a la carpeta **proxmox**

```bash
cd proxmox
./proxmoxRedirect80.sh    # Redirecciona el port 80 cap al SERVER_PORT
./proxmoxRedirectUndo.sh  # Desfà la redirecció anterior

./proxmoxRun.sh           # Compila el servidor i el puja al servidor remot
./proxmoxStop.sh          # Atura el servidor remot
```

També podeu pasar la configuració per paràmetres:

```bash
cd proxmox
./proxmoxRedirect80.sh nomUsuari "$HOME/Desktop/Proxmox IETI/id_rsa" 3001
```

**Nota:** Recordeu a aturar el servidor abans de pujar-lo!

# Gestió de manera manual

### Generar l'arxiu .jar

```bash
./run.sh com.server.Main build
```

*(O amb .\run.sh com.server.Main build)*

Ha de llistar: target/server-package.jar

### Enviar el .jar al servidor

```bash
scp -i id_rsa -P 20127 ./target/server-package.jar nomUsuari@ieticloudpro.ieti.cat:~/
```

### Activar la redirecció del port 80 cap al 3000

A la consola remota del Proxmox:

```bash
ssh -i id_rsa -p 20127 nomUsuari@ieticloudpro.ieti.cat
```

Un cop dins del servidor remot, activar la redirecció:

```bash
sudo iptables-save -t nat | grep -q -- "--dport 80" || sudo iptables -t nat -A PREROUTING -p tcp --dport 80 -j REDIRECT --to-port 3000
sudo iptables -t nat -L -n -v
```

### Fer anar el servidor en segon plà:

```bash
nohup java -jar server-package.jar > output.log 2>&1 &
```

### Aturar el servidor

Per aturar el servidor, cal mirar el seu número de procés:

```bash
ps aux | grep 'java -jar server-package.jar'
```

Amb el número de procés, el podem aturar (suposem 4459):

```bash
kill 4459
```

### Esborrar la redirecció dels ports

```bash
sudo iptables -t nat -D PREROUTING -p tcp --dport 80 -j REDIRECT --to-port 3000
sudo iptables -t nat -L -n -v
```

