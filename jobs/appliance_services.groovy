import conjur.Appliance

def artifacts = '*.deb, *=*'

use(conjur.Conventions) {
  Appliance.getServices().each { service ->
    def serviceJob = job(service) {
      description("""
        <p>Builds packages and tests ${service}.</p>
        <p>Created by 'appliance_services.groovy'</p>
      """.stripIndent())

      if (service == 'authz') {
        wrappers {
          rvm('2.0.0@conjur-authz')
        }
      }

      steps {
        /* XXX authz's bundle doesn't install properly on the executor
         * as of 20160329. Leave this out till it gets sorted out:

        if (service == 'authz') {
          shell('''
            gem install -N bundler
            bundle install --without "production appliance"
          '''.stripIndent())
        }
        */
        shell('./jenkins.sh')
      }

      publishers {
        archiveJunit('spec/reports/*.xml, features/reports/*.xml, reports/*.xml')
        postBuildScripts {
          steps {
            shell('''
              #!/bin/bash -ex

              export DEBUG=true
              export GLI_DEBUG=true

              DISTRIBUTION=$(cat VERSION_APPLIANCE)
              COMPONENT=$(echo \${GIT_BRANCH#origin/} | tr '/' '.')

              if [ "$COMPONENT" == "master" ] || [ "$COMPONENT" == "v$DISTRIBUTION" ]; then
                COMPONENT=stable
              fi

              echo "Publishing $JOB_NAME to distribution '$DISTRIBUTION', component '$COMPONENT'"
              
              if [ -f VERSION ]; then
                VERSION="$(cat VERSION)-$(git rev-parse --short HEAD)"

                debify publish -v $VERSION --component $COMPONENT $DISTRIBUTION $JOB_NAME
              else
                debify publish --component $COMPONENT $DISTRIBUTION $JOB_NAME

                VERSION=$(git describe --long --tags --abbrev=7 --match 'v*.*.*' | sed -e 's/^v//')
              fi

              touch "DISTRIBUTION=\$DISTRIBUTION"
              touch "COMPONENT=\$COMPONENT"
              touch "VERSION=\$VERSION"
            '''.stripIndent())
          }
          onlyIfBuildSucceeds(true)
        }
        archiveArtifacts(artifacts)
      }

      configure { project ->
        project / 'properties' << 'hudson.plugins.copyartifact.CopyArtifactPermissionProperty' {
          projectNameList {
            string "${service}"
          }
        }
      }
    }
    serviceJob.applyCommonConfig()
    serviceJob.addGitRepo("git@github.com:conjurinc/${service}.git")
  }
}
