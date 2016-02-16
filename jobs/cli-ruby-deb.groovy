use(conjur.Conventions) {
  def job = job('cli-ruby-deb') {
    description('Builds a non-Omnibus deb for the Ruby CLI')

    parameters {
      stringParam('DISTRIBUTION', '5.0', 'apt distribution to push package to')
    }

    wrappers {
      rvm('2.0.0@cli-ruby-deb')
    }

    steps {
      shell('./build-deb.sh')
    }

    publishers {
      archiveJunit('spec/reports/*.xml, features/reports/*.xml, acceptance-features/reports/*.xml')
      postBuildScripts {
        steps {
          shell('./publish.sh $DISTRIBUTION')
        }
        onlyIfBuildSucceeds(true)
      }
    }
  }
  job.applyCommonConfig()
  job.addGitRepo('git@github.com:conjurinc/cli-ruby.git')
}
