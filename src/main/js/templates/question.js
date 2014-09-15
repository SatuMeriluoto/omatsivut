; module.exports = function(listApp) {
  listApp.directive("question", function (RecursionHelper) {
    return {
      restrict: 'E',
      scope: {
        questionNode: '=questionNode',
        application: '=application',
        level: '=level'
      },
      templateUrl: 'templates/question.html',
      compile: function (element) {
        return RecursionHelper.compile(element, function ($scope, iElement, iAttrs, controller, transcludeFn) {
          $scope.isGroup = function () {
            return $scope.questionNode && !_.isEmpty($scope.questionNode.questionNodes)
          }
        })
      }
    }
  })
}