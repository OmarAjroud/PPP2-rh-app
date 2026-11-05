package com.hrapp.rh.assistant;

import java.util.List;

/** Les vraies définitions des outils, sans dépendances (le service n'est construit que pour les lire). */
final class TestOutils {

    private TestOutils() {}

    static List<OutilRh> definitions() {
        return new OutilsRhService(null, null, null, null, null, null, null).definitions();
    }
}
