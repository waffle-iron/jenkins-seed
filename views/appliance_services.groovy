import conjur.Appliance

listView('Appliance Services') {
  description('Services built into the Conjur appliance')
  filterBuildQueue()

  jobs {
    Appliance.getServices().each { service ->
      name(service)
    }
  }
  columns {
    status()
    weather()
    name()
    lastSuccess()
    lastFailure()
    lastDuration()
    buildButton()
  }
}