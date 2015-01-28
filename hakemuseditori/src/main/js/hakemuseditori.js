module.exports = function(listApp) {
  require('./applicationValidator')(listApp)
  require('./angularBacon')(listApp)
  require('./directives/confirm')(listApp)
  require('./directives/question')(listApp)
  require('./directives/localizedLink')(listApp)
  require('./directives/formattedTime')(listApp)
  require('./directives/sortable')(listApp)
  require('./directives/disableClickFocus')(listApp)
  require('./directives/application')(listApp)
  require('./directives/hakutoiveenVastaanotto')(listApp)
  require('./directives/ilmoittautuminen')(listApp)
  require('./directives/hakutoiveet')(listApp)
  require('./directives/valintatulos')(listApp)
  require('./directives/henkilotiedot')(listApp)
  require('./directives/applicationPeriods')(listApp)
  require('./directives/clearableInput')(listApp)
  require('./directives/callout')(listApp)
}

module.exports.Hakemus = require("./hakemus")
