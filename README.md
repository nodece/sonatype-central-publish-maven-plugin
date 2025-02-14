# sonatype-central-publish-maven-plugin

A maven plugin to publish the project artifacts to sonatype central.

## Usage

```xml
<build>
  <plugins>
    <plugin>
      <groupId>io.github.nodece</groupId>
      <artifactId>sonatype-central-publish-maven-plugin</artifactId>
      <version>${sonatype-central-publish-maven-plugin.version}</version>
      <extensions>true</extensions>
      <configuration>
        <skip>false</skip>
        <serverId>central</serverId>
      </configuration>
    </plugin>
  </plugins>
</build>
```

### Authentication

To configure authentication, add your credentials to the `~/.m2/settings.xml` file:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username><!-- Username --></username>
      <password><!-- Password --></password>
    </server>
  </servers>
</settings>
```

Alternatively, you can specify your credentials directly in the `<configuration>` section of the `sonatype-central-publish-maven-plugin`:

```xml
<configuration>
  <username><!-- Username --></username>
  <password><!-- Password --></password>
</configuration>
```

### Skip Deployment

To skip the deployment process, set the `<skip>` configuration to `true` in the `<configuration>` section of the `sonatype-central-publish-maven-plugin`:

```xml
<configuration>
  <skip>true</skip>
</configuration>
```

Alternatively, the `sonatype-central-publish-maven-plugin` can also use the `<skip>true</skip>` configuration from the `maven-deploy-plugin` to skip deployment.

## Publish your project artifacts

```shell
mvn deploy
```

Note: This command will only upload the artifacts to sonatype central and does not verify the deployment.

## Limitations

- SNAPSHOT is not supported.
