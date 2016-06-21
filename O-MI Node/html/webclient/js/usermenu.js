(function() {
    $(function() {

      var currentUser;

      if (document.cookie == "")
      {
        $('<li><a href="/security/Login">Login</a></li>').appendTo('#user_menu');
      } else {

        console.log("Found cookie:" + document.cookie);

        $.ajax({
          type: "GET",
          url: "/security/PermissionService?getUserInfo=true",
          contentType: "text/xml",
          processData: false,
          dataType: "text",
          error: function(response) {
            console.log(response);
          },
          success: function(response) {

              console.log("Read current user successfully! Response:"+response);

              if (response == "") {
                $('<li><a href="/security/Login">Login</a></li>').appendTo('#user_menu');
              } else {
                currentUser = JSON.parse(response);
                $('<li><a>'+currentUser['username']+'</a></li>').appendTo('#user_menu');
                $('<li><a href="/security/Login?logout=true">Logout</a></li>').appendTo('#user_menu');
              }
          }
        });
      }
    });
}).call(this);
