package com.hrapp.rh;

import com.hrapp.rh.integration.IntegrationTest;
import org.junit.jupiter.api.Test;

/** L'application démarre sur une base vierge : migrations Flyway, validation du schéma par Hibernate, toutes les requêtes JPQL. */
class RhApplicationIT extends IntegrationTest {

	@Test
	void contextLoads() {
	}

}
