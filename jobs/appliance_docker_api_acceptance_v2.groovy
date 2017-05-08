use(conjur.Conventions) {
  def job = job('appliance-docker-api-acceptance-v2') {
    description('Run API acceptance tests on Docker Conjur')

    parameters {
      stringParam('NAME', '', 'Appliance image name to test. Required.')
      stringParam('TAG', '', 'Appliance image tag to test. Required.')
    }

    concurrentBuild()
    throttleConcurrentBuilds {
      categories(['resource-intensive'])
    }

    steps {
      shell('cd test; ./api-acceptance.sh $NAME $TAG')
    }

    publishers {
      archiveJunit('test/output/report/api-acceptance/*.xml')
      archiveArtifacts('test/output/**')
    }
  }

  job.addGitRepo('git@github.com:conjurinc/appliance.git', false)
  job.applyCommonConfig(notifyRepeatedFailure: true, concurrent: false, label: 'executor-v2')
  job.setBuildName('#${BUILD_NUMBER} ${GIT_BRANCH}: ${ENV,var="APPLIANCE_IMAGE_TAG"}')
}