INSERT INTO units_of_measure(id, code, name, description, active)
VALUES
 (md5('unit-of-measure:EA')::uuid,  'EA',  'Each',       'Count unit; symbol ea.', TRUE),
 (md5('unit-of-measure:PC')::uuid,  'PC',  'Piece',      'Count unit; symbol pc.', TRUE),
 (md5('unit-of-measure:PK')::uuid,  'PK',  'Pack',       'Package unit; symbol pk.', TRUE),
 (md5('unit-of-measure:BOX')::uuid, 'BOX', 'Box',        'Package unit; symbol box.', TRUE),
 (md5('unit-of-measure:CS')::uuid,  'CS',  'Case',       'Package unit; symbol cs.', TRUE),
 (md5('unit-of-measure:BTL')::uuid, 'BTL', 'Bottle',     'Package unit; symbol btl.', TRUE),
 (md5('unit-of-measure:CAN')::uuid, 'CAN', 'Can',        'Package unit; symbol can.', TRUE),
 (md5('unit-of-measure:BAG')::uuid, 'BAG', 'Bag',        'Package unit; symbol bag.', TRUE),
 (md5('unit-of-measure:KG')::uuid,  'KG',  'Kilogram',   'Weight unit; symbol kg.', TRUE),
 (md5('unit-of-measure:G')::uuid,   'G',   'Gram',       'Weight unit; symbol g.', TRUE),
 (md5('unit-of-measure:L')::uuid,   'L',   'Litre',      'Volume unit; symbol L.', TRUE),
 (md5('unit-of-measure:ML')::uuid,  'ML',  'Millilitre', 'Volume unit; symbol mL.', TRUE)
ON CONFLICT (code) DO NOTHING;
