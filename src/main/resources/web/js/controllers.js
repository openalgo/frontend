var phonecatApp = angular.module('phonecatApp', []);

phonecatApp.controller('PhoneListCtrl', function ($scope, $http) {
    $http.get('/stockPrices?ticker=TRUE').success(function (json) {
        $scope.prices = json;
    });
});