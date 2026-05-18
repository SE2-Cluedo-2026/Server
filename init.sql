CREATE TABLE IF NOT EXISTS game (
    game_id         VARCHAR(50)  PRIMARY KEY,
    status          VARCHAR(20)  NOT NULL,
    current_phase   VARCHAR(30)  NOT NULL
    );

CREATE TABLE IF NOT EXISTS turn_manager (
    game_id             VARCHAR(50)  PRIMARY KEY,
    current_player_id   INT          NOT NULL,
    dice_value          INT          NOT NULL,
    phase               VARCHAR(30)  NOT NULL,
    CONSTRAINT fk_turn_manager_game FOREIGN KEY (game_id) REFERENCES game(game_id)
    );

CREATE TABLE IF NOT EXISTS case_file (
    game_id         VARCHAR(50)  PRIMARY KEY,
    suspect_card_id VARCHAR(50)  NOT NULL,
    suspect_name    VARCHAR(100) NOT NULL,
    room_card_id    VARCHAR(50)  NOT NULL,
    room_name       VARCHAR(100) NOT NULL,
    weapon_card_id  VARCHAR(50)  NOT NULL,
    weapon_name     VARCHAR(100) NOT NULL,
    CONSTRAINT fk_case_file_game FOREIGN KEY (game_id) REFERENCES game(game_id)
    );

CREATE TABLE IF NOT EXISTS player (
    player_id           VARCHAR(50)  PRIMARY KEY,
    game_id             VARCHAR(50)  NOT NULL,
    character_type      VARCHAR(50),
    ready               BOOLEAN      NOT NULL DEFAULT FALSE,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    eliminated          BOOLEAN      NOT NULL DEFAULT FALSE,
    cheat_used          BOOLEAN      NOT NULL DEFAULT FALSE,
    accusation_used     BOOLEAN      NOT NULL DEFAULT FALSE,
    position_type       VARCHAR(10),
    position_x          INT,
    position_y          INT,
    position_room       VARCHAR(50),
    CONSTRAINT fk_player_game FOREIGN KEY (game_id) REFERENCES game(game_id)
    );

CREATE TABLE IF NOT EXISTS player_card (
    player_id   VARCHAR(50)  NOT NULL,
    game_id     VARCHAR(50)  NOT NULL,
    card_id     VARCHAR(50)  NOT NULL,
    card_name   VARCHAR(100) NOT NULL,
    card_type   VARCHAR(20)  NOT NULL,
    PRIMARY KEY (player_id, card_id),
    CONSTRAINT fk_pc_player FOREIGN KEY (player_id) REFERENCES player(player_id),
    CONSTRAINT fk_pc_game   FOREIGN KEY (game_id)   REFERENCES game(game_id)
    );

CREATE TABLE IF NOT EXISTS seen_cards (
    player_id   VARCHAR(50)  NOT NULL,
    game_id     VARCHAR(50)  NOT NULL,
    card_id     VARCHAR(50)  NOT NULL,
    card_name   VARCHAR(100) NOT NULL,
    card_type   VARCHAR(20)  NOT NULL,
    PRIMARY KEY (player_id, card_id),
    CONSTRAINT fk_sc_player FOREIGN KEY (player_id) REFERENCES player(player_id),
    CONSTRAINT fk_sc_game   FOREIGN KEY (game_id)   REFERENCES game(game_id)
    );