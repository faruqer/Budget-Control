package app.budgetcontrol;

import android.app.Application;
import android.content.Context;

import app.budgetcontrol.data.Repo;

public class App extends Application {
    private Repo repo;

    public static Repo repo(Context context) {
        return ((App) context.getApplicationContext()).getRepo();
    }

    private synchronized Repo getRepo() {
        if (repo == null) repo = new Repo(this);
        return repo;
    }
}
