sudo yum install java-1.8.0-openjdk-devel
wget https://dlcdn.apache.org/maven/maven-3/3.6.3/binaries/apache-maven-3.6.3-bin.tar.gz
sudo tar xf ./apache-maven-*.tar.gz -C /opt
sudo ln -s /opt/apache-maven-3.6.3 /opt/maven
echo 'export JAVA_HOME=/usr/lib/jvm/java-1.8.0-openjdk-1.8.0.342.b07-1.amzn2.0.1.x86_64' >> maven.sh
echo 'export M2_HOME=/opt/maven' >> maven.sh
echo 'export MAVEN_HOME=/opt/maven' >> maven.sh
echo 'export PATH=${M2_HOME}/bin:${PATH}' >> maven.sh
sudo mv maven.sh /etc/profile.d/
rm apache-maven-3.6.3-bin.tar.gz
sudo chmod +x /etc/profile.d/maven.sh
source /etc/profile.d/maven.sh
sudo yum install git-all
git clone https://github.com/burningwave/miscellaneous-services.git
cd miscellaneous-services
mvn clean dependency:list install
screen -d -m sudo java \
-Dspring.profiles.active=burningwave,ssl \
-cp . \
-jar ./target/site-1.0.0.jar \
--APPLICATION_AUTHORIZATION_TOKEN=yourToken \
--GITHUB_CONNECTOR_AUTHORIZATION_TOKEN=yourToken \
--IO_GITHUB_TOOL_FACTORY_NEXUS_AUTHORIZATION_TOKEN=yourToken \
--ORG_BURNINGWAVE_NEXUS_AUTHORIZATION_TOKEN=yourToken \
--SCHEDULED_OPERATIONS_PING_CRON=- \
--SERVER_SSL_KEY_STORE_PASSWORD=changeit \
--SERVER_SSL_KEY_PASSWORD=changeit