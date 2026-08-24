CREATE TABLE currencies (
    id UUID PRIMARY KEY,
    code VARCHAR(3) NOT NULL,
    name VARCHAR(180) NOT NULL,
    symbol VARCHAR(8) NOT NULL,
    decimal_places INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_currencies_code UNIQUE (code),
    CONSTRAINT ck_currencies_code CHECK (code = upper(code) AND length(code) = 3),
    CONSTRAINT ck_currencies_name_nonblank CHECK (length(trim(name)) > 0),
    CONSTRAINT ck_currencies_decimal_places CHECK (decimal_places >= 0)
);

CREATE INDEX idx_currencies_active ON currencies (active);

INSERT INTO currencies (id, code, name, symbol, decimal_places, active)
VALUES
    ('30000000-0000-0000-0000-000000000001', 'CAD', 'Canadian Dollar', '$', 2, TRUE),
    ('30000000-0000-0000-0000-000000000002', 'USD', 'United States Dollar', '$', 2, TRUE)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    symbol = EXCLUDED.symbol,
    decimal_places = EXCLUDED.decimal_places,
    active = EXCLUDED.active,
    updated_at = NOW();

ALTER TABLE countries ADD COLUMN IF NOT EXISTS alpha3_code VARCHAR(3);
ALTER TABLE countries ADD COLUMN IF NOT EXISTS default_currency_id UUID;
ALTER TABLE countries ADD COLUMN IF NOT EXISTS default_language_code VARCHAR(16) NOT NULL DEFAULT 'en';
ALTER TABLE countries ADD COLUMN IF NOT EXISTS display_order INTEGER NOT NULL DEFAULT 1000;

UPDATE countries SET alpha3_code = code WHERE alpha3_code IS NULL;
UPDATE countries SET alpha3_code = 'CAN', default_currency_id = (SELECT id FROM currencies WHERE code = 'CAD'), default_language_code = 'en', display_order = 10 WHERE code = 'CA';

INSERT INTO countries (id, code, alpha3_code, name, default_currency_id, default_language_code, active, display_order, created_at, updated_at, version)
VALUES ('20000000-0000-0000-0000-000000000001', 'US', 'USA', 'United States', (SELECT id FROM currencies WHERE code = 'USD'), 'en', TRUE, 20, NOW(), NOW(), 0)
ON CONFLICT (code) DO UPDATE SET
    alpha3_code = EXCLUDED.alpha3_code,
    name = EXCLUDED.name,
    default_currency_id = EXCLUDED.default_currency_id,
    default_language_code = EXCLUDED.default_language_code,
    active = EXCLUDED.active,
    display_order = EXCLUDED.display_order,
    updated_at = NOW();

ALTER TABLE countries ALTER COLUMN alpha3_code SET NOT NULL;
ALTER TABLE countries ADD CONSTRAINT uq_countries_alpha3_code UNIQUE (alpha3_code);
ALTER TABLE countries ADD CONSTRAINT fk_countries_default_currency FOREIGN KEY (default_currency_id) REFERENCES currencies (id);
CREATE INDEX idx_countries_display_order ON countries (display_order);

CREATE TABLE country_currencies (
    id UUID PRIMARY KEY,
    country_id UUID NOT NULL,
    currency_id UUID NOT NULL,
    default_currency BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_country_currencies_country_currency UNIQUE (country_id, currency_id),
    CONSTRAINT fk_country_currencies_country FOREIGN KEY (country_id) REFERENCES countries (id),
    CONSTRAINT fk_country_currencies_currency FOREIGN KEY (currency_id) REFERENCES currencies (id)
);

CREATE INDEX idx_country_currencies_country ON country_currencies (country_id);
CREATE INDEX idx_country_currencies_currency ON country_currencies (currency_id);
CREATE INDEX idx_country_currencies_active ON country_currencies (active);

INSERT INTO country_currencies (id, country_id, currency_id, default_currency, active)
VALUES
    ('30000000-0000-0000-0000-000000000101', (SELECT id FROM countries WHERE code = 'CA'), (SELECT id FROM currencies WHERE code = 'CAD'), TRUE, TRUE),
    ('30000000-0000-0000-0000-000000000102', (SELECT id FROM countries WHERE code = 'US'), (SELECT id FROM currencies WHERE code = 'USD'), TRUE, TRUE)
ON CONFLICT (country_id, currency_id) DO UPDATE SET
    default_currency = EXCLUDED.default_currency,
    active = EXCLUDED.active,
    updated_at = NOW();

ALTER TABLE administrative_areas ADD COLUMN IF NOT EXISTS default_timezone_id UUID;
ALTER TABLE administrative_areas ADD COLUMN IF NOT EXISTS default_tax_region_id UUID;
ALTER TABLE administrative_areas ADD COLUMN IF NOT EXISTS display_order INTEGER NOT NULL DEFAULT 1000;

UPDATE administrative_areas
SET display_order = CASE code
    WHEN 'AB' THEN 10 WHEN 'BC' THEN 20 WHEN 'MB' THEN 30 WHEN 'NB' THEN 40 WHEN 'NL' THEN 50
    WHEN 'NS' THEN 60 WHEN 'NT' THEN 70 WHEN 'NU' THEN 80 WHEN 'ON' THEN 90 WHEN 'PE' THEN 100
    WHEN 'QC' THEN 110 WHEN 'SK' THEN 120 WHEN 'YT' THEN 130 ELSE display_order END
WHERE country_id = (SELECT id FROM countries WHERE code = 'CA');

INSERT INTO administrative_areas (id, country_id, code, name, type, active, display_order, created_at, updated_at, version)
VALUES
    ('20000000-0000-0000-0000-000000000101', (SELECT id FROM countries WHERE code = 'US'), 'AL', 'Alabama', 'STATE', TRUE, 10, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000102', (SELECT id FROM countries WHERE code = 'US'), 'AK', 'Alaska', 'STATE', TRUE, 20, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000103', (SELECT id FROM countries WHERE code = 'US'), 'AZ', 'Arizona', 'STATE', TRUE, 30, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000104', (SELECT id FROM countries WHERE code = 'US'), 'AR', 'Arkansas', 'STATE', TRUE, 40, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000105', (SELECT id FROM countries WHERE code = 'US'), 'CA', 'California', 'STATE', TRUE, 50, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000106', (SELECT id FROM countries WHERE code = 'US'), 'CO', 'Colorado', 'STATE', TRUE, 60, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000107', (SELECT id FROM countries WHERE code = 'US'), 'CT', 'Connecticut', 'STATE', TRUE, 70, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000108', (SELECT id FROM countries WHERE code = 'US'), 'DE', 'Delaware', 'STATE', TRUE, 80, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000109', (SELECT id FROM countries WHERE code = 'US'), 'FL', 'Florida', 'STATE', TRUE, 90, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000110', (SELECT id FROM countries WHERE code = 'US'), 'GA', 'Georgia', 'STATE', TRUE, 100, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000111', (SELECT id FROM countries WHERE code = 'US'), 'HI', 'Hawaii', 'STATE', TRUE, 110, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000112', (SELECT id FROM countries WHERE code = 'US'), 'ID', 'Idaho', 'STATE', TRUE, 120, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000113', (SELECT id FROM countries WHERE code = 'US'), 'IL', 'Illinois', 'STATE', TRUE, 130, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000114', (SELECT id FROM countries WHERE code = 'US'), 'IN', 'Indiana', 'STATE', TRUE, 140, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000115', (SELECT id FROM countries WHERE code = 'US'), 'IA', 'Iowa', 'STATE', TRUE, 150, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000116', (SELECT id FROM countries WHERE code = 'US'), 'KS', 'Kansas', 'STATE', TRUE, 160, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000117', (SELECT id FROM countries WHERE code = 'US'), 'KY', 'Kentucky', 'STATE', TRUE, 170, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000118', (SELECT id FROM countries WHERE code = 'US'), 'LA', 'Louisiana', 'STATE', TRUE, 180, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000119', (SELECT id FROM countries WHERE code = 'US'), 'ME', 'Maine', 'STATE', TRUE, 190, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000120', (SELECT id FROM countries WHERE code = 'US'), 'MD', 'Maryland', 'STATE', TRUE, 200, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000121', (SELECT id FROM countries WHERE code = 'US'), 'MA', 'Massachusetts', 'STATE', TRUE, 210, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000122', (SELECT id FROM countries WHERE code = 'US'), 'MI', 'Michigan', 'STATE', TRUE, 220, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000123', (SELECT id FROM countries WHERE code = 'US'), 'MN', 'Minnesota', 'STATE', TRUE, 230, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000124', (SELECT id FROM countries WHERE code = 'US'), 'MS', 'Mississippi', 'STATE', TRUE, 240, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000125', (SELECT id FROM countries WHERE code = 'US'), 'MO', 'Missouri', 'STATE', TRUE, 250, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000126', (SELECT id FROM countries WHERE code = 'US'), 'MT', 'Montana', 'STATE', TRUE, 260, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000127', (SELECT id FROM countries WHERE code = 'US'), 'NE', 'Nebraska', 'STATE', TRUE, 270, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000128', (SELECT id FROM countries WHERE code = 'US'), 'NV', 'Nevada', 'STATE', TRUE, 280, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000129', (SELECT id FROM countries WHERE code = 'US'), 'NH', 'New Hampshire', 'STATE', TRUE, 290, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000130', (SELECT id FROM countries WHERE code = 'US'), 'NJ', 'New Jersey', 'STATE', TRUE, 300, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000131', (SELECT id FROM countries WHERE code = 'US'), 'NM', 'New Mexico', 'STATE', TRUE, 310, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000132', (SELECT id FROM countries WHERE code = 'US'), 'NY', 'New York', 'STATE', TRUE, 320, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000133', (SELECT id FROM countries WHERE code = 'US'), 'NC', 'North Carolina', 'STATE', TRUE, 330, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000134', (SELECT id FROM countries WHERE code = 'US'), 'ND', 'North Dakota', 'STATE', TRUE, 340, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000135', (SELECT id FROM countries WHERE code = 'US'), 'OH', 'Ohio', 'STATE', TRUE, 350, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000136', (SELECT id FROM countries WHERE code = 'US'), 'OK', 'Oklahoma', 'STATE', TRUE, 360, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000137', (SELECT id FROM countries WHERE code = 'US'), 'OR', 'Oregon', 'STATE', TRUE, 370, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000138', (SELECT id FROM countries WHERE code = 'US'), 'PA', 'Pennsylvania', 'STATE', TRUE, 380, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000139', (SELECT id FROM countries WHERE code = 'US'), 'RI', 'Rhode Island', 'STATE', TRUE, 390, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000140', (SELECT id FROM countries WHERE code = 'US'), 'SC', 'South Carolina', 'STATE', TRUE, 400, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000141', (SELECT id FROM countries WHERE code = 'US'), 'SD', 'South Dakota', 'STATE', TRUE, 410, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000142', (SELECT id FROM countries WHERE code = 'US'), 'TN', 'Tennessee', 'STATE', TRUE, 420, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000143', (SELECT id FROM countries WHERE code = 'US'), 'TX', 'Texas', 'STATE', TRUE, 430, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000144', (SELECT id FROM countries WHERE code = 'US'), 'UT', 'Utah', 'STATE', TRUE, 440, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000145', (SELECT id FROM countries WHERE code = 'US'), 'VT', 'Vermont', 'STATE', TRUE, 450, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000146', (SELECT id FROM countries WHERE code = 'US'), 'VA', 'Virginia', 'STATE', TRUE, 460, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000147', (SELECT id FROM countries WHERE code = 'US'), 'WA', 'Washington', 'STATE', TRUE, 470, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000148', (SELECT id FROM countries WHERE code = 'US'), 'WV', 'West Virginia', 'STATE', TRUE, 480, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000149', (SELECT id FROM countries WHERE code = 'US'), 'WI', 'Wisconsin', 'STATE', TRUE, 490, NOW(), NOW(), 0),
    ('20000000-0000-0000-0000-000000000150', (SELECT id FROM countries WHERE code = 'US'), 'WY', 'Wyoming', 'STATE', TRUE, 500, NOW(), NOW(), 0)
ON CONFLICT (country_id, code) DO UPDATE SET
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    active = EXCLUDED.active,
    display_order = EXCLUDED.display_order,
    updated_at = NOW();

CREATE INDEX IF NOT EXISTS idx_administrative_areas_country_active ON administrative_areas (country_id, active);
CREATE INDEX IF NOT EXISTS idx_administrative_areas_display_order ON administrative_areas (country_id, display_order);

CREATE TABLE timezone_reference (
    id UUID PRIMARY KEY,
    iana_name VARCHAR(64) NOT NULL,
    display_name VARCHAR(180) NOT NULL,
    country_id UUID,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    display_order INTEGER NOT NULL DEFAULT 1000,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_timezone_reference_iana_name UNIQUE (iana_name),
    CONSTRAINT fk_timezone_reference_country FOREIGN KEY (country_id) REFERENCES countries (id),
    CONSTRAINT ck_timezone_reference_iana_nonblank CHECK (length(trim(iana_name)) > 0)
);

CREATE INDEX idx_timezone_reference_country ON timezone_reference (country_id);
CREATE INDEX idx_timezone_reference_active ON timezone_reference (active);

INSERT INTO timezone_reference (id, iana_name, display_name, country_id, active, display_order)
VALUES
    ('30000000-0000-0000-0000-000000000201', 'America/St_Johns', 'Newfoundland Time', (SELECT id FROM countries WHERE code = 'CA'), TRUE, 10),
    ('30000000-0000-0000-0000-000000000202', 'America/Halifax', 'Atlantic Time', (SELECT id FROM countries WHERE code = 'CA'), TRUE, 20),
    ('30000000-0000-0000-0000-000000000203', 'America/Moncton', 'Atlantic Time - Moncton', (SELECT id FROM countries WHERE code = 'CA'), TRUE, 30),
    ('30000000-0000-0000-0000-000000000204', 'America/Toronto', 'Eastern Time', (SELECT id FROM countries WHERE code = 'CA'), TRUE, 40),
    ('30000000-0000-0000-0000-000000000205', 'America/Winnipeg', 'Central Time', (SELECT id FROM countries WHERE code = 'CA'), TRUE, 50),
    ('30000000-0000-0000-0000-000000000206', 'America/Regina', 'Central Time - Saskatchewan', (SELECT id FROM countries WHERE code = 'CA'), TRUE, 60),
    ('30000000-0000-0000-0000-000000000207', 'America/Edmonton', 'Mountain Time', (SELECT id FROM countries WHERE code = 'CA'), TRUE, 70),
    ('30000000-0000-0000-0000-000000000208', 'America/Vancouver', 'Pacific Time', (SELECT id FROM countries WHERE code = 'CA'), TRUE, 80),
    ('30000000-0000-0000-0000-000000000209', 'America/Whitehorse', 'Yukon Time', (SELECT id FROM countries WHERE code = 'CA'), TRUE, 90),
    ('30000000-0000-0000-0000-000000000210', 'America/New_York', 'Eastern Time', (SELECT id FROM countries WHERE code = 'US'), TRUE, 110),
    ('30000000-0000-0000-0000-000000000211', 'America/Chicago', 'Central Time', (SELECT id FROM countries WHERE code = 'US'), TRUE, 120),
    ('30000000-0000-0000-0000-000000000212', 'America/Denver', 'Mountain Time', (SELECT id FROM countries WHERE code = 'US'), TRUE, 130),
    ('30000000-0000-0000-0000-000000000213', 'America/Los_Angeles', 'Pacific Time', (SELECT id FROM countries WHERE code = 'US'), TRUE, 140),
    ('30000000-0000-0000-0000-000000000214', 'America/Phoenix', 'Mountain Time - Arizona', (SELECT id FROM countries WHERE code = 'US'), TRUE, 150),
    ('30000000-0000-0000-0000-000000000215', 'America/Anchorage', 'Alaska Time', (SELECT id FROM countries WHERE code = 'US'), TRUE, 160),
    ('30000000-0000-0000-0000-000000000216', 'Pacific/Honolulu', 'Hawaii Time', (SELECT id FROM countries WHERE code = 'US'), TRUE, 170),
    ('30000000-0000-0000-0000-000000000217', 'America/Boise', 'Mountain Time - Idaho', (SELECT id FROM countries WHERE code = 'US'), TRUE, 180)
ON CONFLICT (iana_name) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    country_id = EXCLUDED.country_id,
    active = EXCLUDED.active,
    display_order = EXCLUDED.display_order,
    updated_at = NOW();

CREATE TABLE administrative_division_timezone (
    id UUID PRIMARY KEY,
    administrative_division_id UUID NOT NULL,
    timezone_id UUID NOT NULL,
    default_timezone BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_administrative_division_timezone UNIQUE (administrative_division_id, timezone_id),
    CONSTRAINT fk_administrative_division_timezone_division FOREIGN KEY (administrative_division_id) REFERENCES administrative_areas (id),
    CONSTRAINT fk_administrative_division_timezone_timezone FOREIGN KEY (timezone_id) REFERENCES timezone_reference (id)
);

CREATE INDEX idx_administrative_division_timezone_division ON administrative_division_timezone (administrative_division_id);
CREATE INDEX idx_administrative_division_timezone_timezone ON administrative_division_timezone (timezone_id);

INSERT INTO administrative_division_timezone (id, administrative_division_id, timezone_id, default_timezone)
SELECT md5(area.id::text || timezone.iana_name)::uuid, area.id, timezone.id, mapping.default_timezone
FROM (
    VALUES
        ('CA','AB','America/Edmonton',TRUE), ('CA','BC','America/Vancouver',TRUE), ('CA','MB','America/Winnipeg',TRUE),
        ('CA','NB','America/Moncton',TRUE), ('CA','NL','America/St_Johns',TRUE), ('CA','NS','America/Halifax',TRUE),
        ('CA','ON','America/Toronto',TRUE), ('CA','PE','America/Halifax',TRUE), ('CA','QC','America/Toronto',TRUE),
        ('CA','SK','America/Regina',TRUE), ('CA','NT','America/Edmonton',TRUE), ('CA','NU','America/Toronto',TRUE),
        ('CA','NU','America/Winnipeg',FALSE), ('CA','YT','America/Whitehorse',TRUE),
        ('US','AL','America/Chicago',TRUE), ('US','AK','America/Anchorage',TRUE), ('US','AZ','America/Phoenix',TRUE),
        ('US','AR','America/Chicago',TRUE), ('US','CA','America/Los_Angeles',TRUE), ('US','CO','America/Denver',TRUE),
        ('US','CT','America/New_York',TRUE), ('US','DE','America/New_York',TRUE), ('US','FL','America/New_York',TRUE),
        ('US','FL','America/Chicago',FALSE), ('US','GA','America/New_York',TRUE), ('US','HI','Pacific/Honolulu',TRUE),
        ('US','ID','America/Boise',TRUE), ('US','ID','America/Los_Angeles',FALSE), ('US','IL','America/Chicago',TRUE),
        ('US','IN','America/New_York',TRUE), ('US','IN','America/Chicago',FALSE), ('US','IA','America/Chicago',TRUE),
        ('US','KS','America/Chicago',TRUE), ('US','KS','America/Denver',FALSE), ('US','KY','America/New_York',TRUE),
        ('US','KY','America/Chicago',FALSE), ('US','LA','America/Chicago',TRUE), ('US','ME','America/New_York',TRUE),
        ('US','MD','America/New_York',TRUE), ('US','MA','America/New_York',TRUE), ('US','MI','America/New_York',TRUE),
        ('US','MI','America/Chicago',FALSE), ('US','MN','America/Chicago',TRUE), ('US','MS','America/Chicago',TRUE),
        ('US','MO','America/Chicago',TRUE), ('US','MT','America/Denver',TRUE), ('US','NE','America/Chicago',TRUE),
        ('US','NE','America/Denver',FALSE), ('US','NV','America/Los_Angeles',TRUE), ('US','NH','America/New_York',TRUE),
        ('US','NJ','America/New_York',TRUE), ('US','NM','America/Denver',TRUE), ('US','NY','America/New_York',TRUE),
        ('US','NC','America/New_York',TRUE), ('US','ND','America/Chicago',TRUE), ('US','ND','America/Denver',FALSE),
        ('US','OH','America/New_York',TRUE), ('US','OK','America/Chicago',TRUE), ('US','OR','America/Los_Angeles',TRUE),
        ('US','OR','America/Boise',FALSE), ('US','PA','America/New_York',TRUE), ('US','RI','America/New_York',TRUE),
        ('US','SC','America/New_York',TRUE), ('US','SD','America/Chicago',TRUE), ('US','SD','America/Denver',FALSE),
        ('US','TN','America/Chicago',TRUE), ('US','TN','America/New_York',FALSE), ('US','TX','America/Chicago',TRUE),
        ('US','TX','America/Denver',FALSE), ('US','UT','America/Denver',TRUE), ('US','VT','America/New_York',TRUE),
        ('US','VA','America/New_York',TRUE), ('US','WA','America/Los_Angeles',TRUE), ('US','WV','America/New_York',TRUE),
        ('US','WI','America/Chicago',TRUE), ('US','WY','America/Denver',TRUE)
) AS mapping(country_code, division_code, iana_name, default_timezone)
JOIN countries country ON country.code = mapping.country_code
JOIN administrative_areas area ON area.country_id = country.id AND area.code = mapping.division_code
JOIN timezone_reference timezone ON timezone.iana_name = mapping.iana_name
ON CONFLICT (administrative_division_id, timezone_id) DO UPDATE SET
    default_timezone = EXCLUDED.default_timezone,
    updated_at = NOW();

UPDATE administrative_areas area
SET default_timezone_id = mapping.timezone_id
FROM administrative_division_timezone mapping
WHERE mapping.administrative_division_id = area.id
  AND mapping.default_timezone = TRUE;

ALTER TABLE administrative_areas ADD CONSTRAINT fk_administrative_areas_default_timezone FOREIGN KEY (default_timezone_id) REFERENCES timezone_reference (id);

INSERT INTO tax_jurisdictions (id, country_id, administrative_area_id, code, name, type, active, created_at, updated_at, version)
SELECT md5('tax-jurisdiction:' || country.code || ':' || area.code)::uuid,
       country.id,
       area.id,
       country.code || '-' || area.code,
       area.name || ' tax jurisdiction',
       area.type,
       TRUE,
       NOW(),
       NOW(),
       0
FROM countries country
JOIN administrative_areas area ON area.country_id = country.id
WHERE country.code = 'US'
ON CONFLICT (country_id, code) DO UPDATE SET
    administrative_area_id = EXCLUDED.administrative_area_id,
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    active = EXCLUDED.active,
    updated_at = NOW();

CREATE TABLE tax_regions (
    id UUID PRIMARY KEY,
    country_id UUID NOT NULL,
    administrative_division_id UUID,
    tax_jurisdiction_id UUID,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(180) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    default_for_division BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tax_regions_code UNIQUE (code),
    CONSTRAINT fk_tax_regions_country FOREIGN KEY (country_id) REFERENCES countries (id),
    CONSTRAINT fk_tax_regions_division FOREIGN KEY (administrative_division_id) REFERENCES administrative_areas (id),
    CONSTRAINT fk_tax_regions_jurisdiction FOREIGN KEY (tax_jurisdiction_id) REFERENCES tax_jurisdictions (id),
    CONSTRAINT ck_tax_regions_code_nonblank CHECK (length(trim(code)) > 0),
    CONSTRAINT ck_tax_regions_name_nonblank CHECK (length(trim(name)) > 0)
);

CREATE INDEX idx_tax_regions_country ON tax_regions (country_id);
CREATE INDEX idx_tax_regions_division ON tax_regions (administrative_division_id);
CREATE INDEX idx_tax_regions_active ON tax_regions (active);

INSERT INTO tax_regions (id, country_id, administrative_division_id, tax_jurisdiction_id, code, name, active, default_for_division)
SELECT md5('tax-region:' || country.code || ':' || area.code)::uuid,
       country.id,
       area.id,
       jurisdiction.id,
       country.code || '-' || area.code,
       area.name || ' tax region',
       TRUE,
       TRUE
FROM countries country
JOIN administrative_areas area ON area.country_id = country.id
LEFT JOIN tax_jurisdictions jurisdiction ON jurisdiction.country_id = country.id AND jurisdiction.administrative_area_id = area.id
WHERE country.code IN ('CA', 'US')
ON CONFLICT (code) DO UPDATE SET
    country_id = EXCLUDED.country_id,
    administrative_division_id = EXCLUDED.administrative_division_id,
    tax_jurisdiction_id = EXCLUDED.tax_jurisdiction_id,
    name = EXCLUDED.name,
    active = EXCLUDED.active,
    default_for_division = EXCLUDED.default_for_division,
    updated_at = NOW();

UPDATE administrative_areas area
SET default_tax_region_id = region.id
FROM tax_regions region
WHERE region.administrative_division_id = area.id
  AND region.default_for_division = TRUE;

ALTER TABLE administrative_areas ADD CONSTRAINT fk_administrative_areas_default_tax_region FOREIGN KEY (default_tax_region_id) REFERENCES tax_regions (id);

ALTER TABLE stores ADD COLUMN IF NOT EXISTS country_id UUID;
ALTER TABLE stores ADD COLUMN IF NOT EXISTS administrative_division_id UUID;
ALTER TABLE stores ADD COLUMN IF NOT EXISTS currency_id UUID;
ALTER TABLE stores ADD COLUMN IF NOT EXISTS timezone_id UUID;
ALTER TABLE stores ADD COLUMN IF NOT EXISTS timezone_name VARCHAR(64);
ALTER TABLE stores ADD COLUMN IF NOT EXISTS tax_region_id UUID;
ALTER TABLE stores ADD COLUMN IF NOT EXISTS tax_region_code VARCHAR(64);

UPDATE stores store
SET country_id = country.id
FROM countries country
WHERE upper(store.country_code) = country.code
  AND store.country_id IS NULL;

UPDATE stores store
SET administrative_division_id = area.id
FROM administrative_areas area
WHERE area.country_id = store.country_id
  AND upper(store.administrative_area_code) = area.code
  AND store.administrative_division_id IS NULL;

UPDATE stores store
SET currency_id = currency.id
FROM currencies currency
WHERE upper(store.currency_code) = currency.code
  AND store.currency_id IS NULL;

UPDATE stores store
SET timezone_id = timezone.id,
    timezone_name = timezone.iana_name
FROM timezone_reference timezone
WHERE store.timezone = timezone.iana_name
  AND store.timezone_id IS NULL;

UPDATE stores store
SET tax_region_id = region.id,
    tax_region_code = region.code
FROM tax_regions region
WHERE region.administrative_division_id = store.administrative_division_id
  AND region.default_for_division = TRUE
  AND store.tax_region_id IS NULL;

ALTER TABLE stores ADD CONSTRAINT fk_stores_country FOREIGN KEY (country_id) REFERENCES countries (id);
ALTER TABLE stores ADD CONSTRAINT fk_stores_administrative_division FOREIGN KEY (administrative_division_id) REFERENCES administrative_areas (id);
ALTER TABLE stores ADD CONSTRAINT fk_stores_currency FOREIGN KEY (currency_id) REFERENCES currencies (id);
ALTER TABLE stores ADD CONSTRAINT fk_stores_timezone FOREIGN KEY (timezone_id) REFERENCES timezone_reference (id);
ALTER TABLE stores ADD CONSTRAINT fk_stores_tax_region FOREIGN KEY (tax_region_id) REFERENCES tax_regions (id);

CREATE INDEX idx_stores_country_id ON stores (country_id);
CREATE INDEX idx_stores_administrative_division_id ON stores (administrative_division_id);
CREATE INDEX idx_stores_currency_id ON stores (currency_id);
CREATE INDEX idx_stores_timezone_id ON stores (timezone_id);
CREATE INDEX idx_stores_tax_region_id ON stores (tax_region_id);
CREATE INDEX idx_stores_tax_region_code ON stores (tax_region_code);

INSERT INTO security_permissions (id, code, description)
VALUES
    (md5('permission:COUNTRY_REFERENCE_VIEW')::UUID, 'COUNTRY_REFERENCE_VIEW', 'View country reference data.'),
    (md5('permission:CURRENCY_REFERENCE_VIEW')::UUID, 'CURRENCY_REFERENCE_VIEW', 'View currency reference data.'),
    (md5('permission:TIMEZONE_REFERENCE_VIEW')::UUID, 'TIMEZONE_REFERENCE_VIEW', 'View timezone reference data.'),
    (md5('permission:TAX_REGION_REFERENCE_VIEW')::UUID, 'TAX_REGION_REFERENCE_VIEW', 'View tax-region reference data.'),
    (md5('permission:STORE_CREATE')::UUID, 'STORE_CREATE', 'Create stores.'),
    (md5('permission:STORE_UPDATE')::UUID, 'STORE_UPDATE', 'Update stores.'),
    (md5('permission:STORE_LOCATION_UPDATE')::UUID, 'STORE_LOCATION_UPDATE', 'Update store geographic configuration.'),
    (md5('permission:STORE_CURRENCY_OVERRIDE')::UUID, 'STORE_CURRENCY_OVERRIDE', 'Override default country currency for a store.')
ON CONFLICT (code) DO NOTHING;

INSERT INTO security_role_permissions (id, role_id, permission_id)
SELECT md5('role-permission:' || role.name || ':' || permission.code)::UUID, role.id, permission.id
FROM (
    VALUES
        ('OWNER', 'COUNTRY_REFERENCE_VIEW'),
        ('OWNER', 'CURRENCY_REFERENCE_VIEW'),
        ('OWNER', 'TIMEZONE_REFERENCE_VIEW'),
        ('OWNER', 'TAX_REGION_REFERENCE_VIEW'),
        ('OWNER', 'STORE_CREATE'),
        ('OWNER', 'STORE_UPDATE'),
        ('OWNER', 'STORE_LOCATION_UPDATE'),
        ('OWNER', 'STORE_CURRENCY_OVERRIDE'),
        ('MANAGER', 'COUNTRY_REFERENCE_VIEW'),
        ('MANAGER', 'CURRENCY_REFERENCE_VIEW'),
        ('MANAGER', 'TIMEZONE_REFERENCE_VIEW'),
        ('MANAGER', 'TAX_REGION_REFERENCE_VIEW'),
        ('MANAGER', 'STORE_CREATE'),
        ('MANAGER', 'STORE_UPDATE'),
        ('MANAGER', 'STORE_LOCATION_UPDATE'),
        ('CASHIER', 'COUNTRY_REFERENCE_VIEW'),
        ('CASHIER', 'CURRENCY_REFERENCE_VIEW'),
        ('CASHIER', 'TIMEZONE_REFERENCE_VIEW'),
        ('CASHIER', 'TAX_REGION_REFERENCE_VIEW')
) AS grants(role_name, permission_code)
JOIN security_roles role ON role.name = grants.role_name
JOIN security_permissions permission ON permission.code = grants.permission_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
