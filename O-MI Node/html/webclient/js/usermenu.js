(function() {
    $(function() {

      if (document.cookie == "")
      {
        $('<li><a href="/security/Login">Login</a></li>').appendTo('#user_menu');
      } else {
        $('<li><a>'+document.cookie+'</a></li>').appendTo('#user_menu');
        $('<li><a href="/security/Login">Logout</a></li>').appendTo('#user_menu');
      }

    });
}).call(this);
