use(conjur.Conventions) {
  def job = matrixJob('conjur-cookbook-matrix') {
    description('''
      Runs test-kitchen suites of the
      <a href="https://github.com/conjur-cookbooks/conjur">conjur</a> cookbook
      in parallel on EC2.
      <br><br>
      Update the SUITES parameter to run a subset of the tests as needed. The names
      are space-delimited and use the same regex as test-kitchen. For example, to run
      all centos and ubuntu suites set SUITES to "centos ubuntu".
    '''.stripIndent())

    parameters {
      stringParam('SUITES', '', 'test-kitchen suites to run')
    }

    axes {
      label('label', 'docker') // Restrict to run child jobs on slaves tagged 'docker'
      configure { axes ->
        axes << 'ca.silvermaplesolutions.jenkins.plugins.daxis.DynamicAxis' {
          name 'SUITE'
          values {
            string 'SUITES'
          }
          varName 'SUITES'
          axisValues {
            string 'default'
          }
        }
      }
    }

    steps {
      shell('''
        summon -f secrets.ci.yml ./matrix.sh env.properties --only ${SUITE}
      '''.stripIndent())
    }

    publishers {
      archiveJunit('ci/reports/*.xml, spec/*.xml')
    }
  }
  job.applyCommonConfig()
  job.addGitRepo('git@github.com:conjur-cookbooks/conjur.git', false)
}
