# encoding: UTF-8
# This file is auto-generated from the current state of the database. Instead
# of editing this file, please use the migrations feature of Active Record to
# incrementally modify your database, and then regenerate this schema definition.
#
# Note that this schema.rb definition is the authoritative source for your
# database schema. If you need to create the application database on another
# system, you should be using db:schema:load, not running all the migrations
# from scratch. The latter is a flawed and unsustainable approach (the more migrations
# you'll amass, the slower it'll run and the greater likelihood for issues).
#
# It's strongly recommended that you check this file into your version control system.

ActiveRecord::Schema.define(version: 20150703224433) do

  # These are extensions that must be enabled in order to support this database
  enable_extension "plpgsql"
  enable_extension "uuid-ossp"

  create_table "certs", id: :uuid, default: "uuid_generate_v4()", force: :cascade do |t|
    t.uuid     "user_id",    null: false
    t.string   "role",       null: false
    t.text     "cert_pem",   null: false
    t.text     "key_pem",    null: false
    t.datetime "created_at", null: false
    t.integer  "version",    null: false
    t.datetime "expired_at", null: false
  end

  add_index "certs", ["user_id", "version"], name: "index_certs_on_user_id_and_version", unique: true, using: :btree

  create_table "nodes", id: :uuid, default: "uuid_generate_v4()", force: :cascade do |t|
    t.uuid     "user_id",                null: false
    t.integer  "status",     default: 0, null: false
    t.string   "role",                   null: false
    t.integer  "version",    default: 0, null: false
    t.datetime "updated_at",             null: false
    t.datetime "created_at",             null: false
  end

  add_index "nodes", ["user_id", "role", "version"], name: "index_nodes_on_user_id_and_role_and_version", unique: true, using: :btree

  create_table "users", id: :uuid, default: "uuid_generate_v4()", force: :cascade do |t|
    t.uuid     "parent_id"
    t.string   "email",                  null: false
    t.string   "token",                  null: false
    t.integer  "role",       default: 0, null: false
    t.datetime "created_at",             null: false
  end

  add_index "users", ["email"], name: "index_users_on_email", unique: true, using: :btree

  add_foreign_key "certs", "users", name: "certs_user_id_fk", on_delete: :restrict
  add_foreign_key "nodes", "users", name: "nodes_user_id_fk", on_delete: :restrict
  add_foreign_key "users", "users", column: "parent_id", name: "users_parent_id_fk", on_delete: :restrict
end
