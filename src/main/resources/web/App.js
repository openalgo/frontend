function studentController($scope, $http) {
    var url = "data.txt";

    $http.get(url).success(function (response) {
        $scope.students = response;
    });
}