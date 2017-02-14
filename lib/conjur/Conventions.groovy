package conjur

import javaposse.jobdsl.dsl.Job

class Conventions {
  static {
    Map.metaClass.fetch = { key, defaultValue ->
        delegate.containsKey(key) ? delegate[key] : defaultValue
    }
  }

  // Applies common configuration to a job
  static void applyCommonConfig(Job job, Map args=[:]) {
    def cleanup = args.fetch('cleanup', true)
    def notifyOnRepeatedFailure = args.fetch('notifyRepeatedFailure', false)
    def args_label = args.fetch('label', 'executor')

    job.with {
      label(args_label)
      logRotator(60, -1, 60, -1)  // keep builds/artifacts for 60 days

      concurrentBuild()
      throttleConcurrentBuilds {
        maxPerNode(1)
      }

      wrappers {
        // note: necessary because of broken permissions from the
        // docker build process; remove after fixing that
        if (cleanup) {
          preBuildCleanup()
        }

        colorizeOutput()
        timestamps()
        buildName('#${BUILD_NUMBER} ${GIT_BRANCH}')
      }

      publishers {
        // slackNotifier {
        //   room('jenkins')
        //   notifyFailure(true)
        //   notifyRepeatedFailure(notifyOnRepeatedFailure)
        //   notifyUnstable(true)
        //   notifyBackToNormal(true)
        //   commitInfoChoice('AUTHORS_AND_TITLES')
        // }
      }
    }
  }

  // Listens for Github pushes, defines a BRANCH param for overriding
  static void addGitRepo(Job job, String repoUrl, boolean triggerOnPush=true, String listenOn='') {
    job.with {
      parameters {
        stringParam('BRANCH', listenOn, 'Git branch or SHA to build. Not required.')
      }

      scm {
        git {
          remote {
            url(repoUrl)
          }
          branch('$BRANCH')
          extensions {
            cleanBeforeCheckout()
          }
        }
      }

      if (triggerOnPush) {
        triggers {
          githubPush()
        }
      }
    }
  }

  // Overrides the build name, shown in Job page left column
  static void setBuildName(Job job, String name) {
    job.with {
      wrappers {
        buildName(name)
      }
    }
  }

  static void publishDebsOnSuccess(Job job) {
    job.with {
      publishers {
        postBuildScripts {
          onlyIfBuildSucceeds()
          steps {
            shell('''
            DISTRIBUTION=$(cat VERSION_APPLIANCE)
            COMPONENT=$(echo \${GIT_BRANCH#origin/} | tr '/' '.')

            if [ "$COMPONENT" == "master" ] || [ "$COMPONENT" == "v$DISTRIBUTION" ]; then
              COMPONENT=stable
            fi

            echo "Publishing $JOB_NAME to distribution '$DISTRIBUTION', component '$COMPONENT'"

            if [ -f VERSION ]; then
              VERSION="$(debify detect-version | tail -n 1)"
            else
              VERSION=$(git describe --long --tags --abbrev=7 --match 'v*.*.*' | sed -e 's/^v//')
            fi

            rm -f *latest*.deb

            cat << YML > secrets.yml
            ARTIFACTORY_USERNAME: !var artifactory/users/jenkins/username
            ARTIFACTORY_PASSWORD: !var artifactory/users/jenkins/password
            YML

            summon debify publish -d $DISTRIBUTION -c $COMPONENT *.deb
            '''.stripIndent())
          }
        }
      }
    }
  }

  // Publish build artifacts to Artifactory
  static void publishToArtifactory(Job job, String targetRepo, String artifactRegex, String deploymentProperties) {
    job.with {
      configure { project ->
        project / buildWrappers << 'org.jfrog.hudson.generic.ArtifactoryGenericConfigurator' {
          details {
            artifactoryName('-1280243840@1442969665256')
            artifactoryUrl('https://conjurinc.artifactoryonline.com/conjurinc')
            deployReleaseRepository {
              keyFromSelect(targetRepo)
              dynamicMode(false)
            }
          }
          deployPattern(artifactRegex)
          matrixParams(deploymentProperties)
          deployBuildInfo(true)
          includeEnvVars(false)
          discardOldBuilds(false)
          discardBuildArtifacts(false)
        }
      }
    }
  }
}
